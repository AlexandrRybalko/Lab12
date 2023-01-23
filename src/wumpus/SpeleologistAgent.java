package wumpus;

import aima.core.environment.wumpusworld.WumpusAction;
import aima.core.environment.wumpusworld.WumpusPercept;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpeleologistAgent extends Agent {
    private AID environmentAgent;
    private AID navigatorAgent;
    private Map<String, ArrayList<String>> sentenceDictionary;

    @Override
    protected void setup() {
        sentenceDictionary = createDictionary();

        addBehaviour(new FindEnvironmentBehaviour(this, 5000));
        addBehaviour(new FindNavigatorBehaviour(this, 5000));
    }

    private void CheckInitialized(){
        if (environmentAgent != null && navigatorAgent != null){
            addBehaviour(new CommunicationBehaviour());
        }
    }

    private class FindEnvironmentBehaviour extends TickerBehaviour{
        public FindEnvironmentBehaviour(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            System.out.println("#############environmet tick " + System.currentTimeMillis());
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("take-a-walk-into-the-treasure-cave");
            template.addServices(sd);
            template.addLanguages("en");

            try{
                DFAgentDescription[] result = DFService.search(myAgent, template);

                if (result.length > 0){
                    environmentAgent = result[0].getName();
                    myAgent.removeBehaviour(this);
                    CheckInitialized();
                }
            }
            catch (FIPAException e){
                e.printStackTrace();
            }
        }
    }

    private class FindNavigatorBehaviour extends TickerBehaviour{
        public FindNavigatorBehaviour(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            System.out.println("#############navigator tick " + System.currentTimeMillis());
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("navigator");
            template.addServices(sd);
            template.addLanguages("en");

            try{
                DFAgentDescription[] result = DFService.search(myAgent, template);

                if (result.length > 0){
                    navigatorAgent = result[0].getName();
                    myAgent.removeBehaviour(this);
                    CheckInitialized();
                }
            }
            catch (FIPAException e){
                e.printStackTrace();
            }
        }
    }

    private class CommunicationBehaviour extends Behaviour{
        private MessageTemplate mt;
        private String lastReceivedPerceptString;
        private int step = 0;
        private String messageToNavigatorContent;
        private ACLMessage messageToNavigator;
        private WumpusAction lastAction = WumpusAction.FORWARD;
        private boolean hasAgentClimbedOut;

        @Override
        public void action() {
            switch(step){
                case 0:
                    ACLMessage message = new ACLMessage(ACLMessage.REQUEST);
                    message.addReceiver(environmentAgent);
                    message.setConversationId("location-request");
                    myAgent.send(message);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId(message.getConversationId()),
                            MessageTemplate.MatchPerformative(ACLMessage.INFORM));
                    step = 1;
                    break;
                case 1:
                    ACLMessage reply = myAgent.receive(mt);

                    if (reply != null){
                        lastReceivedPerceptString = reply.getContent();
                        step = 2;
                    }
                    else {
                        block();
                    }

                    break;
                case 2:
                    messageToNavigatorContent = createNaturalLanguageMessage();
                    step = 3;
                    break;
                case 3:
                    messageToNavigator = new ACLMessage(ACLMessage.INFORM);
                    messageToNavigator.setConversationId("navigating");
                    messageToNavigator.addReceiver(navigatorAgent);
                    messageToNavigator.setContent(messageToNavigatorContent);
                    myAgent.send(messageToNavigator);
                    step = 4;
                    break;
                case 4:
                    MessageTemplate template = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                            MessageTemplate.MatchConversationId("navigating"));
                    ACLMessage messageFromNavigator = myAgent.receive(template);

                    if (messageFromNavigator != null){
                        if (containsString(messageFromNavigator.getContent(), "FORWARD")){
                            lastAction = WumpusAction.FORWARD;
                            step = 5;
                        }
                        if (containsString(messageFromNavigator.getContent(), "LEFT")){
                            lastAction = WumpusAction.TURN_LEFT;
                            step = 5;
                        }
                        if (containsString(messageFromNavigator.getContent(), "RIGHT")){
                            lastAction = WumpusAction.TURN_RIGHT;
                            step = 5;
                        }
                        if (containsString(messageFromNavigator.getContent(), "GRAB")){
                            lastAction = WumpusAction.GRAB;
                            step = 5;
                        }
                        if (containsString(messageFromNavigator.getContent(), "SHOOT")){
                            lastAction = WumpusAction.SHOOT;
                            step = 5;
                        }
                        if (containsString(messageFromNavigator.getContent(), "CLIMB")){
                            lastAction = WumpusAction.CLIMB;
                            step = 5;
                        }
                    }
                    else{
                        block();
                    }

                    break;
                case 5:
                    ACLMessage messageToEnvironment = new ACLMessage(ACLMessage.CFP);
                    messageToEnvironment.addReceiver(environmentAgent);
                    messageToEnvironment.setConversationId("request-to-perform-action");
                    messageToEnvironment.setContent(lastAction.toString());
                    myAgent.send(messageToEnvironment);
                    step = 6;

                    if (lastAction == WumpusAction.CLIMB){
                        hasAgentClimbedOut = true;
                    }

                    break;
                case 6:
                    mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL),
                            MessageTemplate.MatchConversationId("request-to-perform-action"));
                    ACLMessage replyFromEnvironment = myAgent.receive(mt);

                    if (replyFromEnvironment != null){
                        if (hasAgentClimbedOut){
                            myAgent.doDelete();
                            System.out.println("");
                        }
                        else {
                            step = 0;
                        }
                    }
                    else{
                        block();
                    }

                    break;
            }
        }

        @Override
        public boolean done() {
            return hasAgentClimbedOut;
        }

        private String createNaturalLanguageMessage(){
            String result = "";
            String randomSentence;
            WumpusPercept p = deserializeWumpusPercept(lastReceivedPerceptString);

            if (p.isStench()){
                randomSentence = getRandomSentence("stench");
                result += randomSentence;
            }
            if (p.isBreeze()){
                randomSentence = getRandomSentence("breeze");

                if (result.isEmpty()){
                    result += randomSentence;
                }
                else{
                    result = result + ". " + randomSentence;
                }
            }
            if (p.isGlitter()){
                randomSentence = getRandomSentence("glitter");

                if (result.isEmpty()){
                    result += randomSentence;
                }
                else{
                    result = result + ". " + randomSentence;
                }
            }
            if (p.isBump()){
                randomSentence = getRandomSentence("bump");

                if (result.isEmpty()){
                    result += randomSentence;
                }
                else{
                    result = result + ". " + randomSentence;
                }
            }
            if (p.isScream()){
                randomSentence = getRandomSentence("scream");

                if (result.isEmpty()){
                    result += randomSentence;
                }
                else{
                    result = result + ". " + randomSentence;
                }
            }
            if (result.isEmpty()){
                result = "I feel nothing";
            }

            return result;
        }

        private boolean containsString(String s, String stringToBeFound){
            Pattern pattern = Pattern.compile(stringToBeFound, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(s);

            return matcher.find();
        }
    }

    private WumpusPercept deserializeWumpusPercept(String s){
        WumpusPercept result = new WumpusPercept();

        if (s.contains("Stench")){
            result.setStench();
        }
        if (s.contains("Breeze")){
            result.setBreeze();
        }
        if (s.contains("Glitter")){
            result.setGlitter();
        }
        if (s.contains("Bump")){
            result.setBump();
        }
        if (s.contains("Scream")){
            result.setScream();
        }

        return result;
    }

    private Map<String, ArrayList<String>> createDictionary(){
        Map<String, ArrayList<String>> result = new HashMap<>();
        result.put("stench", new ArrayList<>(Arrays.asList(
                "I feel stench",
                "There is a stench in here",
                "The room is full of stench")));
        result.put("breeze", new ArrayList<>(Arrays.asList(
                "I feel breeze",
                "There is a breeze in here",
                "It’s breezing in here")));
        result.put("glitter", new ArrayList<>(Arrays.asList(
                "I can see the glitter",
                "There is the glitter in here",
                "Something is glittering in here")));
        result.put("bump", new ArrayList<>(Arrays.asList(
                "I felt a bump",
                "I bumped into something",
                "Going this way leads to a bump")));
        result.put("scream", new ArrayList<>(Arrays.asList(
                "I heard a scream",
                "Someone is screaming",
                "There is a scream all over the room")));

        return result;
    }

    private String getRandomSentence(String key){
        ArrayList<String> list = sentenceDictionary.get(key);
        int randomIndex = new Random().nextInt(list.size());
        String result = list.get(randomIndex);

        return result;
    }
}
