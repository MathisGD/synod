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

import java.util.ArrayList;
import java.util.Random;

public class Process extends UntypedAbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);// Logger attached to actor

    // OFC State.
    private int n; // number of processes
    private int i; // id of current process
    private Membership processes; // other processes' references
    private Boolean proposal;
    private int ballot;
    private int readBallot;
    private int imposeBallot;
    private Boolean estimate;
    private ArrayList<Pair<Boolean, Integer>> states;
    private int numberAck;

    // Test state.
    private double alpha = 0.5;
    private Boolean faultProne = false;
    private Boolean silent = false;
    private Boolean hold = false;

    // Static method to create an actor.
    public static Props createActor(int n, int i) {
        return Props.create(Process.class, () -> new Process(n, i));
    }

    public Process(int n, int i) {
        this.n = n;
        this.i = i;
        this.ballot = i - n;
        this.imposeBallot = i - n;

        this.states = new ArrayList<Pair<Boolean, Integer>>();
        for (int k = 0; k < n; k++) {
            this.states.add(new Pair<Boolean,Integer>(null, 0));
        }
    }

    private void propose(Boolean v) {
        log.info(this + " - propose(" + v + ")");
        proposal = v;
        ballot += n;

        for (ActorRef process : processes.references) {
            process.tell(new Read(ballot), this.getSelf());
        }
    }

    public void onReceive(Object message) throws Throwable {
        // If the process is fault prone (and not silent yet), it goes silent with a
        // probablity alpha.
        if (!silent & faultProne)
            silent = (new Random()).nextDouble() < alpha;
        // If the process is silent, it doesn't answer anything anymore.
        if (silent)
            return;

        if (message instanceof Membership) {
            log.info(this + " - membership received");
            Membership m = (Membership) message;
            processes = m;


        } else if (message instanceof Launch) {
            log.info(this + " - launch received");
            propose((new Random()).nextBoolean());


        } else if (message instanceof Crash) {
            log.info(this + " - crash received");
            faultProne = true;


        } else if (message instanceof Hold) {
            log.info(this + " - hold received");
            hold = true;


        } else if (message instanceof Read) {
            log.info(this + " - read received");
            Read r = (Read) message;
            if (readBallot > r.ballot || imposeBallot > r.ballot) {
                getSender().tell(new Abort(), this.getSelf());
            } else {
                readBallot = r.ballot;
                getSender().tell(new Gather(r.ballot, imposeBallot, estimate), this.getSelf());
            }


        } else if (message instanceof Abort) {
            log.info(this + " - abort received");
            return;


        } else if (message instanceof Gather) {
            log.info(this + " - gather received");
            Gather g = (Gather) message;
            if (g.estimate != null) {
                states.set(Integer.valueOf(getSender().path().name()), new Pair<Boolean, Integer>(g.estimate, g.imposeBallot));
            }

            int count = 0;
            int highestBallot = 0;
            Boolean estOfHighestBallot = null;
            for (Pair<Boolean, Integer> state : states) {
                if (state.first() != null) {
                    count++;
                    if (state.second() > highestBallot) {
                        highestBallot = state.second();
                        estOfHighestBallot = state.first();
                    }
                }
            }
            if (count > n / 2) {
                if (estOfHighestBallot != null) {
                    proposal = estOfHighestBallot;
                }
                states = new ArrayList<Pair<Boolean, Integer>>();
                for (int i = 0; i < n; i++) {
                    states.add(new Pair<Boolean,Integer>(null, 0));
                }
                for (ActorRef process: processes.references) {
                    process.tell(new Impose(ballot, proposal), this.getSelf());
                }
            }


        } else if (message instanceof Impose) {
            log.info(this + " - impose received");
            Impose i = (Impose) message;
            if (readBallot > i.ballot | imposeBallot > i.ballot) {
                getSender().tell(new Abort(), this.getSelf());
            } else {
                estimate = i.proposal;
                imposeBallot = i.ballot;
                getSender().tell(new Ack(), this.getSelf());
            }
            
            
        } else if (message instanceof Ack) {
            log.info(this + " - ack received");
            numberAck++;
            if (numberAck > n / 2) {
                for (ActorRef process : processes.references) {
                    process.tell(new Decide(proposal), this.getSelf());
                }
            }
            
            
        } else if (message instanceof Decide) {
            log.info(this + " - decide received");
            for (ActorRef process : processes.references) {
                process.tell(new Decide(proposal), this.getSelf());
            }   
        }
    }

    @Override
    public String toString() {
        return "Process #" + i;
    }
}
