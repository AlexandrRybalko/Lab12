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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NavigatorAgent extends Agent {
    private WumpusCave cave;
    private WumpusEnvironment wumpusEnvironment;
    private HybridWumpusAgent hybridWumpusAgent;

    @Override
    protected void setup() {
        System.out.println("#############Navigator created " + System.currentTimeMillis());
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("navigator");
        sd.setName("navigator");
        dfd.addServices(sd);
        dfd.addLanguages("en");

        try{
            DFService.register(this, dfd);
        }
        catch (FIPAException fe){
            fe.printStackTrace();
        }

        addBehaviour(new CheckMailCyclicBehaviour());
        setUpWumpusWorld();
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

    private static WumpusCave createWumpusCave() {
        return new WumpusCave(4, 4, ""
                + ". . . P "
                + "W G P . "
                + ". . . . "
                + "S . P . ");
    }

    private class CheckMailCyclicBehaviour extends CyclicBehaviour{
        private MessageTemplate template;
        private boolean hasClimbedOut;

        @Override
        public void action() {
            template = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchConversationId("navigating"));
            ACLMessage message = myAgent.receive(template);

            if (message != null){
                myAgent.addBehaviour(new OneShotBehaviour() {
                    @Override
                    public void action() {
                        String content = message.getContent();
                        String replyContent = getNaturalLanguageReply(content);

                        ACLMessage reply = message.createReply();
                        reply.setContent(replyContent);
                        myAgent.send(reply);

                        if (hasClimbedOut){
                            myAgent.doDelete();
                        }
                    }
                });
            }
            else{
                block();
            }
        }

        private String getNaturalLanguageReply(String messageContent){
            String result = "";
            WumpusPercept percept = new WumpusPercept();

            if (containsString("stench", messageContent)){
                percept.setStench();
            }
            if (containsString("breez", messageContent)){
                percept.setBreeze();
            }
            if (containsString("glitter", messageContent)){
                percept.setGlitter();
            }
            if (containsString("bump", messageContent)){
                percept.setBump();
            }
            if (containsString("scream", messageContent)){
                percept.setScream();
            }

            Optional<WumpusAction> action = hybridWumpusAgent.act(percept);

            if (action.isPresent()){
                result = action.get().toString();

                switch (action.get()){
                    case FORWARD:
                        result = "Move forward.";
                        break;
                    case TURN_LEFT:
                        result = "Turn left.";
                        break;
                    case TURN_RIGHT:
                        result = "Turn right.";
                        break;
                    case GRAB:
                        result = "Grab gold.";
                        break;
                    case SHOOT:
                        result = "Shoot wumpus.";
                        break;
                    case CLIMB:
                        result = "Climb out.";
                        hasClimbedOut = true;
                        break;
                }

                wumpusEnvironment.execute(hybridWumpusAgent, action.get());
            }

            return result;
        }

        private boolean containsString(String s, String stringToBeFound){
            Pattern pattern = Pattern.compile(stringToBeFound, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(s);

            return matcher.find();
        }
    }
}
