package com.example.synod;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Pair;

import com.example.synod.message.Hold;
import com.example.synod.message.Read;
import com.example.synod.message.Crash;
import com.example.synod.message.Decide;
import com.example.synod.message.Abort;
import com.example.synod.message.Ack;
import com.example.synod.message.Gather;
import com.example.synod.message.Impose;
import com.example.synod.message.Launch;
import com.example.synod.message.Membership;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Process extends UntypedAbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);// Logger attached to actor

    // Synod State.
    private int n; // number of processes
    private int i; // id of current process
    private List<ActorRef> processes; // other processes' references
    private Boolean proposal;
    private int ballot;
    private int readBallot;
    private int imposeBallot;
    private Boolean estimate;
    private int numberAck;
    private Boolean decided;
    private HashMap<ActorRef, Pair<Boolean, Integer>> states;

    // Test State.
    private Boolean faultProne = false; // if true, the process will crash (forever) with proba alpha on each receive
    private double alpha; // crash probability on each receive for fault prone processes
    private Boolean silent = false; // true when the process crashed and don't answer anything anymore
    private Boolean hold = false; // if true, the process stops proposing new values (useful for leader election)
    private ActorRef terminator; // process to tell when you decided
    private Boolean verbose = false;
    
    // Static method to create an actor.
    public static Props createActor(int n, int i, double alpha) {
        return Props.create(Process.class, () -> new Process(n, i, alpha));
    }

    public Process(int n, int i, double alpha) {
        this.n = n;
        this.i = i;
        this.ballot = i - n;
        this.readBallot = 0;
        this.imposeBallot = i - n;
        this.decided = false;
        this.numberAck = 0;
        this.states = new HashMap<>();
        this.alpha = alpha;
    }

    private void propose(Boolean v) {
        if (verbose)
            log.info(this + " - propose(" + v + ")");
        proposal = v;
        // The process estimates its own value for now.
        estimate = v;
        ballot += n;

        for (ActorRef process : processes) {
            process.tell(new Read(ballot), this.getSelf());
        }
    }

    public void onReceive(Object message) throws Throwable {
        // If the process is fault prone (and not silent yet), it goes silent with a
        // probablity alpha.
        if (!silent & faultProne)
            if ((new Random()).nextDouble() < alpha) {
                log.info(this + " - crashes");
                silent = true;
            }
        // If the process is silent, it doesn't answer anything anymore.
        if (silent)
            return;
        // If the process decided, it doesn't answer anything anymore.
        if (decided)
            return;

        if (message instanceof Membership) {
            if (verbose)
                log.info(this + " - membership received");
            Membership m = (Membership) message;
            processes = m.references;
            terminator = m.terminator;

        } else if (message instanceof Launch) {
            if (verbose)
                log.info(this + " - launch received");
            propose((new Random()).nextBoolean());

        } else if (message instanceof Crash) {
            if (verbose)
                log.info(this + " - crash received");
            faultProne = true;

        } else if (message instanceof Hold) {
            log.info(this + " - hold received");
            hold = true;

        } else if (message instanceof Read) {
            if (verbose)
                log.info(this + " - read received");
            Read r = (Read) message;
            if (readBallot > r.ballot || imposeBallot > r.ballot) {
                getSender().tell(new Abort(), this.getSelf());
            } else {
                readBallot = r.ballot;
                getSender().tell(new Gather(r.ballot, imposeBallot, estimate), this.getSelf());
            }

        } else if (message instanceof Abort) {
            if (verbose)
                log.info(this + " - abort received");
            if (!hold) {
                propose((new Random()).nextBoolean());
            }

        } else if (message instanceof Gather) {
            if (verbose)
                log.info(this + " - gather received");
            Gather g = (Gather) message;
            if (g.estimate != null) {
                states.put(getSender(), new Pair<Boolean, Integer>(g.estimate,
                        g.imposeBallot));
            }

            if (states.size() > n / 2) {
                int highestBallot = 0;
                Boolean estOfHighestBallot = null;
                for (Map.Entry<ActorRef, Pair<Boolean, Integer>> state : states.entrySet()) {
                    if (state.getValue().first() != null) {
                        if (state.getValue().second() > highestBallot) {
                            highestBallot = state.getValue().second();
                            estOfHighestBallot = state.getValue().first();
                        }
                    }
                }
                if (highestBallot > 0) {
                    proposal = estOfHighestBallot;
                }
                states.clear();
                for (ActorRef process : processes) {
                    process.tell(new Impose(ballot, proposal), this.getSelf());
                }
            }

        } else if (message instanceof Impose) {
            if (verbose)
                log.info(this + " - impose received");
            Impose i = (Impose) message;
            if (readBallot > i.ballot || imposeBallot > i.ballot) {
                getSender().tell(new Abort(), this.getSelf());
            } else {
                estimate = i.proposal;
                imposeBallot = i.ballot;
                getSender().tell(new Ack(), this.getSelf());
            }

        } else if (message instanceof Ack) {
            if (verbose)
                log.info(this + " - ack received");
            numberAck++;
            if (numberAck > n / 2) {
                numberAck = 0;
                for (ActorRef process : processes) {
                    process.tell(new Decide(proposal), this.getSelf());
                }
            }

        } else if (message instanceof Decide) {
            if (verbose)
                log.info(this + " - decide received");
            decided = true;
            log.info(this + " - DECIDED!!");
            for (ActorRef process : processes) {
                process.tell(new Decide(proposal), this.getSelf());
            }
            terminator.tell(new Decide(proposal), this.getSelf());
        }
    }

    @Override
    public String toString() {
        return "Process #" + i;
    }
}
