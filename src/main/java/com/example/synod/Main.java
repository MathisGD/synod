package com.example.synod;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import com.example.synod.message.Hold;
import com.example.synod.message.Crash;
import com.example.synod.message.Launch;
import com.example.synod.message.Membership;

import java.util.*;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        // Parse command line arguments.
        if (args.length < 4) {
            System.out.println("Usage: java Main <N> <f> <leaderDelay> <alpha>");
            System.exit(1);
        }

        int N = Integer.parseInt(args[0]);
        int f = Integer.parseInt(args[1]);
        int leaderDelay = Integer.parseInt(args[2]);
        double alpha = Double.parseDouble(args[3]);

        // Instantiate an actor system.
        final ActorSystem system = ActorSystem.create("system");
        system.log().info("System started with N=" + N);

        ArrayList<ActorRef> processes = new ArrayList<>();
        ActorRef terminator;

        // Create all actors.
        for (int i = 0; i < N; i++) {
            final ActorRef a = system.actorOf(Process.createActor(N, i, alpha));
            processes.add(a);
        }
        terminator = system.actorOf(Terminator.createActor(N-f));

        // Give each process a view of all the other processes.
        Membership m = new Membership(processes, terminator);
        for (ActorRef process : processes) {
            process.tell(m, ActorRef.noSender());
        }

        Thread.sleep(50);

        // Tell processes to start.
        for (ActorRef process : processes) {
            process.tell(new Launch(), ActorRef.noSender());
        }

        Collections.shuffle(processes);

        // Processes 0, ..., f-1 are chosen to be fault prone.
        // They will crash at some point.
        for (int i = 0; i < f; i++) {
            processes.get(i).tell(new Crash(), ActorRef.noSender());
        }

        Thread.sleep(leaderDelay);

        // Processes[f] is choosen to propose.
        // All the other processes hold (stop proposing).
        System.out.println("Start leader election");
        for (int i = 0; i < N; i++) {
            if (i != f) {
                processes.get(i).tell(new Hold(), ActorRef.noSender());
            }
        }
    }
}
