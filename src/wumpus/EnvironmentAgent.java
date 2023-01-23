package wumpus;

import aima.core.agent.impl.SimpleEnvironmentView;
import aima.core.environment.wumpusworld.*;
import aima.core.logic.propositional.inference.DPLL;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.lang.reflect.Field;

public class EnvironmentAgent extends Agent {
    private HybridWumpusAgent hybridWumpusAgent;
    private WumpusEnvironment wumpusEnvironment;
    private WumpusCave cave;

    @Override
    protected void setup() {
        System.out.println("#############Environment created " + System.currentTimeMillis());
        setUpWumpusWorld();

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("take-a-walk-into-the-treasure-cave");
        sd.setName("environment");
        dfd.addServices(sd);
        dfd.addLanguages("en");

        try{
            DFService.register(this, dfd);
        }
        catch (FIPAException fe){
            fe.printStackTrace();
        }

        addBehaviour(new CheckMailCyclicBehaviour());
    }

    @Override
    protected void takeDown() {
        try{
            DFService.deregister(this);
        }
        catch(FIPAException fe){
            fe.printStackTrace();
        }
    }

    private void setUpWumpusWorld(){
        cave = createWumpusCave();
        wumpusEnvironment = new WumpusEnvironment(cave);
        SimpleEnvironmentView view = new SimpleEnvironmentView();
        wumpusEnvironment.addEnvironmentListener(view);
        hybridWumpusAgent = new HybridWumpusAgent
                (cave.getCaveXDimension(), cave.getCaveYDimension(), cave.getStart(), new DPLL(), wumpusEnvironment);

        wumpusEnvironment.notify("The cave:\n" + cave.toString());
        wumpusEnvironment.addAgent(hybridWumpusAgent);
    }

    private WumpusCave createWumpusCave() {
        return new WumpusCave(4, 4, ""
                + ". . . P "
                + "W G P . "
                + ". . . . "
                + "S . P . ");
    }

    private class CheckMailCyclicBehaviour extends CyclicBehaviour{
        private MessageTemplate cfpTemplate;
        private MessageTemplate requestTemplate;
        private boolean hasAgentClimbedOut;

        @Override
        public void action() {
            cfpTemplate = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                            MessageTemplate.MatchConversationId("location-request"));
            requestTemplate = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.CFP),
                    MessageTemplate.MatchConversationId("request-to-perform-action"));
            ACLMessage message = myAgent.receive(MessageTemplate.or(cfpTemplate, requestTemplate));

            if (message != null){
                if (message.getPerformative() == ACLMessage.CFP){
                    String actionString = message.getContent();
                    performAction(actionString);
                    ACLMessage reply = message.createReply(ACLMessage.ACCEPT_PROPOSAL);
                    reply.setContent("OK");
                    myAgent.send(reply);

                    if (hasAgentClimbedOut){
                        myAgent.doDelete();
                    }
                }else if (message.getPerformative() == ACLMessage.REQUEST){
                    WumpusPercept percept = wumpusEnvironment.getPerceptSeenBy(hybridWumpusAgent);
                    myAgent.addBehaviour(new OneShotBehaviour() {
                        @Override
                        public void action() {
                            ACLMessage reply = message.createReply(ACLMessage.INFORM);
                            reply.setContent(percept.toString() + " t=" + getTimeMark() +
                                    " Position[" + wumpusEnvironment.getAgentPosition(hybridWumpusAgent) + "]");
                            myAgent.send(reply);
                        }
                    });
                }
            }
            else{
                block();
            }
        }

        private void performAction(String s){
            WumpusAction action = WumpusAction.valueOf(s);
            wumpusEnvironment.execute(hybridWumpusAgent, action);

            if (action == WumpusAction.CLIMB){
                hasAgentClimbedOut = true;
                System.out.println("#########################Metrics " + hybridWumpusAgent.getMetrics());
            }
        }
    }

    private int getTimeMark() {
        int result = -1;
        Field timeMarkField = null;

        try {
            timeMarkField = hybridWumpusAgent.getClass().getDeclaredField("t");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

        timeMarkField.setAccessible(true);

        try {
            result = (int)timeMarkField.get(hybridWumpusAgent);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return result;
    }
}
