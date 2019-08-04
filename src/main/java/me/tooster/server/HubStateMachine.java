package me.tooster.server;

import me.tooster.common.Command;
import me.tooster.common.FiniteStateMachine;
import me.tooster.common.Formatter;
import me.tooster.server.MTG.Deck;
import me.tooster.server.MTG.GameStateMachine;
import me.tooster.server.exceptions.CardException;
import me.tooster.server.exceptions.DeckException;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

class HubStateMachine extends FiniteStateMachine<Command.Compiled<ServerCommand>, User> {

    HubStateMachine() { super(Stage.NOT_IN_GAME); }

    enum Stage implements FiniteStateMachine.State<Command.Compiled<ServerCommand>, User> {
        NOT_IN_GAME { // players can import decks and select a deck.

            @Override
            public Stage process(Command.Compiled<ServerCommand> cc, User user) {
                switch (cc.cmd) {
                    case HELP:
                        ServerCommand c = null;
                        try {
                            if (cc.args.length > 0)
                                c = ServerCommand.valueOf(cc.args[0].toUpperCase());
                        } catch (IllegalArgumentException ignored) { }
                        user.transmit(String.join("\n", user.cmdController.help(c)));
                    case LIST_DECKS: {
                        String[] decks = ResourceManager.getInstance().getDecks().toArray(new String[]{});
                        Arrays.sort(decks);
                        cc.getPlayer().transmit(Formatter.response("Decks:\n" + Formatter.list(decks)));
                        return this;
                    }
                    case SHOW_DECK: {
                        Set<Map.Entry<String, Object>> cards =
                                ResourceManager.getInstance().getDeck(cc.args[0]).entrySet();
                        String[] strings =
                                cards.stream().map(e -> e.getKey() + " x" + e.getValue()).toArray(String[]::new);
                        Arrays.sort(strings);

                        cc.getPlayer().transmit(Formatter.response("Cards serverIn '" + cc.args[0] + "':\n"
                                + Formatter.list(strings)));
                        return this;
                    }
                    case SELECT_DECK: {
                        try {
                            Deck deck = Deck.build(cc.getPlayer(), cc.args[1]);
                            cc.getPlayer().setDeck(deck);
                        } catch (DeckException | CardException e) {
                            cc.getPlayer().transmit(Formatter.error(e.getMessage()));
                        }
                        return this;
                    }
                    case READY:
                        if (cc.getPlayer().getDeck() == null) {
                            cc.getPlayer().transmit(Formatter.error("You must select deck first."));
                            return this;
                        } else {
                            cc.getPlayer().getFlags().add(User.Flag.READY);
                            // hub.players cannot be empty here
                            if (!hub.getUsers().stream().allMatch(p -> p.getFlags().contains(User.Flag.READY))) {
                                hub.broadcast(Formatter.broadcast("Waiting for all players to be ready."));
                                return this;
                            }
                        }
                }
                hub.broadcast("Starting a game. " + hub.getUsers().get(0) + " goes first.");
                hub.setGameFSM(new GameStateMachine());
                return IN_GAME;
            }

            @Override
            public void onEnter(State prevState, Hub hub) {
                hub.broadcast(Formatter.broadcast("Waiting for players."));
            }
        },
        // game phase with it's own state machine.
        IN_GAME {
            @Override
            public Stage process(ServerCommand.Compiled, User user) {
                if (cc.command == ServerCommand.END_GAME) {
                    return NOT_IN_GAME;
                }
                return this; // same state
            }

            @Override
            public void onExit(State nextState, Hub hub) {
                hub.broadcast(Formatter.broadcast("Winner: " + hub.getGameFSM().getWinner()));
            }
        };
    }
}
