package com.example.synod;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import com.example.synod.message.Hold;
import com.example.synod.message.Crash;
import com.example.synod.message.Launch;
import com.example.synod.message.Membership;

import java.util.*;

public class Main {
    public static int N = 10;
    public static int f = 0;

    public static void main(String[] args) throws InterruptedException {
        // Instantiate an actor system
        final ActorSystem system = ActorSystem.create("system");
        system.log().info("System started with N=" + N);

        ArrayList<ActorRef> processes = new ArrayList<>();

        for (int i = 0; i < N; i++) {
            final ActorRef a = system.actorOf(Process.createActor(N, i));
            processes.add(a);
        }

        // give each process a view of all the other processes
        Membership m = new Membership(processes);
        for (ActorRef process : processes) {
            process.tell(m, ActorRef.noSender());
        }

        Thread.sleep(50);

        for (ActorRef process : processes) {
            process.tell(new Launch(), ActorRef.noSender());
        }

        Collections.shuffle(processes);

        // processes 0, ..., f-1 are chosen to be fault prone.
        // they will crash at some point.
        for (int i = 0; i < f; i++) {
            processes.get(i).tell(new Crash(), ActorRef.noSender());
        }

        Thread.sleep(1000);

        // processes[f] is choosen to propose.
        // all the other processes hold.
        System.out.println("Start leader election");
        for (int i = 0; i < N; i++) {
            if (i != f) {
                processes.get(i).tell(new Hold(), ActorRef.noSender());
            }
        }
    }
}
