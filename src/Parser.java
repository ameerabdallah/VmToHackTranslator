import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Parser implements AutoCloseable {

    private final BufferedReader bufferedReader;
    private String currentLine;
    private String arg1;
    private int arg2;
    private String command;
    private CommandType commandType;
    Parser(InputStream in) {
        this.bufferedReader = new BufferedReader(new InputStreamReader(in));
    }

    public String getCurrentLine() {
        return currentLine;
    }

    public void advance() throws IOException {
        while (this.hasMoreLines()) {
            String currentLine = this.bufferedReader.readLine().trim();
            if (!currentLine.startsWith("//") && !currentLine.isBlank()) { // is to be saved
                String[] tokens = currentLine.split(" ");
                this.command = tokens[0];

                if (tokens.length > 1) {
                    this.arg1 = tokens[1];
                } else {
                    this.arg1 = null;
                }

                if (tokens.length > 2) { // may not have a second argument
                    this.arg2 = Integer.parseInt(tokens[2]);
                } else {
                    this.arg2 = -1;
                }

                this.commandType = switch (this.command) {
                    case "add", "sub", "neg", "eq", "gt", "lt", "and", "or", "not" -> CommandType.C_ARITHMETIC;
                    case "push" -> CommandType.C_PUSH;
                    case "pop" -> CommandType.C_POP;
                    case "label" -> CommandType.C_LABEL;
                    case "goto" -> CommandType.C_GOTO;
                    case "if-goto" -> CommandType.C_IF;
                    case "function" -> CommandType.C_FUNCTION;
                    case "return" -> CommandType.C_RETURN;
                    case "call" -> CommandType.C_CALL;
                    default -> throw new RuntimeException("Unknown command: " + command);
                };

                this.currentLine = currentLine;
                break;
            }
        }
    }

    public CommandType commandType() {
        return this.commandType;
    }

    public String arg1() {
        if (this.commandType() == CommandType.C_RETURN) {
            throw new RuntimeException("arg1() called on C_RETURN command");
        } else if (this.commandType() == CommandType.C_ARITHMETIC) {
            return this.command;
        } else {
            return this.arg1;
        }
    }

    public int arg2() {
        if (this.commandType().hasArg2()) {
            return this.arg2;
        } else {
            throw new RuntimeException("arg2() called on non-PUSH/POP/FUNCTION/CALL command");
        }
    }

    public boolean hasMoreLines() throws IOException {
        return bufferedReader.ready();
    }

    @Override
    public void close() throws IOException {
        bufferedReader.close();
    }

    public enum CommandType {
        C_ARITHMETIC,
        C_PUSH,
        C_POP,
        C_LABEL,
        C_GOTO,
        C_IF,
        C_FUNCTION,
        C_RETURN,
        C_CALL;

        public boolean isArithmetic() {
            return this == C_ARITHMETIC;
        }

        public boolean isPushPop() {
            return this == C_PUSH || this == C_POP;
        }

        public boolean hasArg2() {
            return this == C_PUSH || this == C_POP || this == C_FUNCTION || this == C_CALL;
        }
    }
}
