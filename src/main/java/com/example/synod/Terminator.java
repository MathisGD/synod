package com.example.synod;

import akka.actor.UntypedAbstractActor;
import akka.actor.Props;

import com.example.synod.message.Decide;

public class Terminator extends UntypedAbstractActor {
    public static Props createActor(int n) {
        return Props.create(Terminator.class, () -> new Terminator());
    }

    public void onReceive(Object message) throws Throwable {
        if (message instanceof Decide) {
            System.out.println("First process decided");
            getContext().getSystem().terminate();
        } else {
            System.out.println("Unexpected msg");
        }
    }
}
