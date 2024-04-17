package com.example.synod.message;

import akka.actor.ActorRef;
import java.util.List;

public class Membership {

    public List<ActorRef> references;
    public ActorRef terminator;

    public Membership(List<ActorRef> references, ActorRef terminator) {
        this.references = references;
        this.terminator = terminator;
    }
}
