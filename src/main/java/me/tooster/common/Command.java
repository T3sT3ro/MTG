package me.tooster.common;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.EnumSet;

/**
 * Interface for commands with alias. Each command can be compiled with code serverIn format
 * <pre>new ServerCommand.Compiled(ServerCommand.ECHO, data)</pre>
 * Command interface works best when used with enums enums. Compiled command
 */
public interface Command {

    /**
     * Used to alias the commands. Command without alias won't be matched. Main alias is the first one
     */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Alias {
        String[] value() default {};
    }

    /**
     * Returns help for the command
     */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Help {
        String value() default "";
    }

    default boolean matches(String input) {
        return (this.getClass().isAnnotationPresent(Alias.class)) &&
                Arrays.stream(this.getClass().getAnnotation(Alias.class).value()).anyMatch(s -> s.equalsIgnoreCase(input.strip()));
    }

    /**
     * @return list of aliases for the command
     */
    default String[] aliases() {
        return this.getClass().isAnnotationPresent(Alias.class)
                ? this.getClass().getAnnotation(Alias.class).value()
                : new String[0];
    }

    /**
     * @return main (first) alias of command or empty string if it doesn't exist
     */
    default String mainAlias() {
        String[] aliases = aliases();
        return aliases.length > 0 ? aliases[0] : "";
    }

    /**
     * @return the acronym of command (second alias) if exists, otherwise returns empty string
     */
    default String acronym() {
        String[] aliases = aliases();
        return aliases.length > 1 ? aliases[1] : "";
    }

    /**
     * @return Returns help message for this command.
     */
    default String help() {
        String alias = mainAlias(), acronym = acronym();
        return this.getClass().isAnnotationPresent(Help.class)
                ?
                (acronym.isEmpty() ? alias.isEmpty() ? "" : alias + " : " : acronym + " " + alias + " : ")
                        + this.getClass().getAnnotation(Help.class).value()
                : "No help for command " + this + " available.";
    }

    class Controller<CMD extends Enum<CMD> & Command> {
        public final Class<CMD>   commandEnumClass;
        public final Object       owner;
        public final EnumSet<CMD> enabledCommands; // by default all  disabled
        public final EnumSet<CMD> commandMask; // commandMask for enable/disable etc. mask is clear at start

        /**
         * Creates a controller for managing commands.
         *
         * @param commandClass class of command that controller controls
         */
        public Controller(Class<CMD> commandClass, Object owner) { //(Class<CMD> enumClass) {
            commandEnumClass = commandClass; //(Class<CMD>) ((CMD) new Object()).getClass();// FIXME: WTF may work hack to get enum
            this.owner = owner;
            enabledCommands = EnumSet.noneOf(commandEnumClass);
            commandMask = EnumSet.noneOf(commandEnumClass);
        }

        /**
         * Sets the enabled commands anew with respect to the mask.
         *
         * @param commands commands to set as enabled, rest will be disabled
         */
        public void setEnabled(CMD... commands) {
            enabledCommands.removeAll(commandMask);
            EnumSet<CMD> cmdSwitch = EnumSet.allOf(commandEnumClass);
            cmdSwitch.retainAll(Arrays.asList(commands));
            cmdSwitch.removeAll(commandMask);
            enabledCommands.addAll(cmdSwitch);
        }

        /**
         * Enables commands with respect to the mask.
         *
         * @param commands commands to enable
         */
        public void enable(CMD... commands) {
            EnumSet<CMD> cmdSwitch = EnumSet.allOf(commandEnumClass);
            cmdSwitch.retainAll(Arrays.asList(commands));
            cmdSwitch.removeAll(commandMask);
            enabledCommands.addAll(cmdSwitch);
        }

        /**
         * Disables commands with respect to the mask.
         *
         * @param commands commands to disable
         */
        public void disable(CMD... commands) {
            EnumSet<CMD> cmdSwitch = EnumSet.allOf(commandEnumClass);
            cmdSwitch.retainAll(Arrays.asList(commands));
            cmdSwitch.removeAll(commandMask);
            enabledCommands.removeAll(cmdSwitch);
        }

        /**
         * @param command command to check
         * @return returns true if command is enabled
         */
        public boolean isEnabled(CMD command) { return enabledCommands.contains(command); }

        /**
         * Sets the mask anew. Masked command is not affected by enable/disable/setEnabled commands.
         *
         * @param commands commands to set serverIn the mask, rest will be unset
         */
        public void setMasked(CMD... commands) {
            commandMask.clear();
            commandMask.addAll(Arrays.asList(commands));
        }

        /**
         * Adds command to mask. Masked command is not affected by enable/disable/setEnabled commands.
         *
         * @param commands command to add to the mask
         */
        public void mask(CMD... commands) {
            EnumSet<CMD> cmdSwitch = EnumSet.allOf(commandEnumClass);
            cmdSwitch.retainAll(Arrays.asList(commands));
            commandMask.addAll(cmdSwitch);
        }

        /**
         * Removes command from the mask. Masked command is not affected by enable/disable/setEnabled commands.
         *
         * @param commands command to remove from the mask
         */
        public void unmask(CMD... commands) {
            EnumSet<CMD> cmdSwitch = EnumSet.allOf(commandEnumClass);
            cmdSwitch.retainAll(Arrays.asList(commands));
            commandMask.removeAll(cmdSwitch);
        }

        /**
         * Checks if command is in mask. Masked command is not affected by enable/disable/setEnabled commands.
         *
         * @param command command to check
         * @return true iff command is set serverIn mask
         */
        public boolean isMasked(CMD command) { return commandMask.contains(command);}

        /**
         * Parses the input and returns compiled command. If command didn't parse, then parsed command is null and
         * arg0 is raw input string. If parsed, args contain raw parts of command.
         *
         * @param input input string to parse
         * @return compiled command with command and string argument list if parse was a success,
         * null and raw input as first argument otherwise
         */
        public @NotNull Compiled<CMD> parse(@NotNull String input) {
            var parts = Formatter.splitParts(input);
            String cname = parts.get(0); // command name
            for (CMD c : commandEnumClass.getEnumConstants())
                if (c.matches(cname))
                    return compile(c, parts.toArray(new String[0])); // rest is argument's list

            return compile(null, input); // returns default command with input
        }

        /**
         * Creates a compiled command object that has it's enum command and notnull string arguments list.
         *
         * @param command command to compile
         * @param args    command arguments
         * @return compiled command
         */
        public Compiled<CMD> compile(@Nullable CMD command, String... args) {
            return new Compiled<>(this, command, args);
        }

        /**
         * Returns help for given command, or help for all commands if argument is null
         *
         * @param command command to check help or null to check all commands
         * @return list of help elements
         */
        public String[] help(@NotNull String command) {
            var parsed = parse(command);
            if (command.isBlank())
                return Arrays.stream(commandEnumClass.getEnumConstants()).toArray(String[]::new);
            if (parsed.cmd != null) // command successfully parsed
                return new String[]{parsed.cmd.help()};
            return new String[0];
        }

        /**
         * @return help for all commands
         */
        public String[] help() { return help(""); }
    }

    /**
     * Compiled command object.
     *
     * @param <CMD> command type
     */
    class Compiled<CMD extends Enum<CMD> & Command> {
        @NotNull public final  Controller<CMD> controller;  // controller that parsed this command
        @Nullable public final CMD             cmd;         // command for parse success, null otherwise
        @NotNull public final  String[]        args;        // arguments as array of strings. arg 0 is command itself so
        // `/test a b' argument list is {"/test", "a", "b"}

        /**
         * Creates new compiled command with final fields <code>command</code> and <code>args</code>. Those fields
         * provide easy access to the parsed command object,
         *
         * @param command command enum representing the command, never null
         * @param args    arguments for command, arg0 should be command itself. Always a not null array.
         *                Always a fresh copy. If args are not specified, then arg0 is defaulted to main alias.
         *                If main alias doesn't exist or command is null, a String[0] array is args
         */
        Compiled(@NotNull Controller<CMD> controller, @Nullable CMD command, String... args) {
            this.controller = controller;
            this.cmd = command;
            this.args = args == null ?
                    command == null || command.mainAlias().isBlank() ?
                            new String[0] :
                            new String[]{command.mainAlias()} :
                    args.clone();
        }

        /**
         * @return true iff command is enabled in it's controller
         */
        public boolean isEnabled() { return controller.isEnabled(this.cmd); }

        /**
         * @return true iff command is serverIn mask serverIn it's controller
         */
        public boolean isMasked() {return controller.isMasked(this.cmd);}


        /**
         * @param idx index of argument starting at 0
         * @return argument if specified or empty string "" if doesn't exist
         */
        public String arg(int idx) {
            return idx >= 0 ? idx < args.length ? args[idx] : "" : "";
        }

        /**
         * Converts command to readable format as <br>
         * <code>
         * player#0789 > DECK testDeck
         * </code>
         */
        @Override
        public String toString() {
            return cmd + " " + String.join(" ", args);
        }
    }

}
