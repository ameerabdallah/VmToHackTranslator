import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.StringJoiner;

public class CodeWriter implements AutoCloseable {

    private static final boolean BOOTSTRAP = true;
    private static int RETURN_INDEX = 0;
    private static int AUTO_BRANCH_INDEX = 0;
    private final BufferedWriter bufferedWriter;
    private String fileName;

    public CodeWriter(OutputStream out) throws IOException {
        this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(out));
        if (BOOTSTRAP) {
            this.fileName = "Sys";
            this.write(
                    "@256",
                    "D=A",
                    "@SP",
                    "M=D", // set SP to 256

                    // set these to known illegal values to help detect errors
                    "@LCL",
                    "M=-1", // set LCL to -1

                    "D=M-1",
                    "@ARG",
                    "M=D", // set ARG to -2

                    "D=M-1",
                    "@THIS",
                    "M=D", // set THIS to -3

                    "D=M-1",
                    "@THAT",
                    "M=D" // set THAT to -4

            );
            this.writeComment("call Sys.init");
            this.writeCall("Sys.init", 0);
        }
    }

    public void setFileName(String fileName) throws IOException {
        this.fileName = fileName;
    }

    public void writeComment(String comment) throws IOException {
        this.bufferedWriter.newLine();
        this.write("// " + comment);
    }

    /**
     * Pops the top one or two values from the stack, performs the operation, and pushes the result back onto the stack.
     */
    public void writeArithmetic(String command) throws IOException {
        ArithmeticCommand arithmeticCommand = ArithmeticCommand.fromString(command);

        switch (arithmeticCommand) {
            case ADD, SUB, EQ, GT, LT, AND, OR -> {
                // pops 2 off the stack and stores them in D and M. if stack is ordered like: X, Y <-SP. Then D will
                // equal Y and
                // M will equal X
                writeStackPop(true);
                writeStackPop(false);
            }
            case NEG, NOT -> writeStackPop(true);
            default -> throw new IllegalArgumentException("Unknown command: " + arithmeticCommand);
        }

        String asmCommands = switch (arithmeticCommand) {
            case ADD, SUB, AND, OR, NEG, NOT -> arithmeticCommand.arithmeticTranslation;
            case EQ, GT, LT -> createEqualityCommands(arithmeticCommand);
        };

        // consistent line breaks
        this.write(asmCommands.split("\n"));

        // push D onto the stack after performing arithmetic
        this.writeStackPush();
    }

    public void writePushPop(Parser.CommandType command, String segment, int index) throws IOException {
        Segment segmentEnum = Segment.fromString(segment);
        switch (command) {
            case C_PUSH -> writePush(segmentEnum, index);
            case C_POP -> writePop(segmentEnum, index);
            default -> throw new IllegalArgumentException("Unknown command type: " + command);
        }
    }

    private void writePush(Segment segment, int index) throws IOException {
        if (segment == Segment.CONSTANT) {
            this.write(
                    "@" + index,
                    "D=A"
            );
        } else {
            writePointToSegment(segment, index, true);
        }
        writeStackPush();
    }

    private void writePop(Segment segment, int index) throws IOException {
        writeStackPop(true);
        writePointToSegment(segment, index, false);
        this.write("M=D");
    }

    public void writeLabel(String label) throws IOException {
        this.write(wrapLabel(label));
    }

    public void writeGoto(String label) throws IOException {
        this.write("@" + label, "0;JMP");
    }

    public void writeIf(String label) throws IOException {
        this.writeStackPop(true);
        this.write("@" + label, "D;JNE"); // jump if D is not 0
    }

    public void writeFunction(String functionName, int nVars) throws IOException {
        this.write(wrapLabel(functionName));
        for (int i = 0; i < nVars; i++) {
            this.writePointToSegment(Segment.LOCAL, i, false);
            this.write("M=0");
        }
    }

    public void writeCall(String functionName, int nArgs) throws IOException {
        // push returnAddress
        String returnLabel = createReturnLabel(functionName);

        // push the return address to the stack
        this.write("@" + returnLabel);
        this.write("D=A");
        this.writeStackPush();

        this.writePush(Segment.LOCAL, -1); // push LCL to the stack
        this.writePush(Segment.ARGUMENT, -1); // push ARG to the stack
        this.writePush(Segment.THIS, -1); // push THIS to the stack
        this.writePush(Segment.THAT, -1); // push THAT to the stack

        // set ARG = SP - (nArgs + 5)
        this.write(
                "@SP",
                "D=M",
                "@" + (nArgs + 5),
                "D=D-A"
        );
        this.write(
                "@" + Segment.ARGUMENT.segment,
                "M=D" // set ARG = SP - (nArgs + 5)
        );

        // set LCL = SP
        this.write(
                "@SP",
                "D=M",
                "@" + Segment.LOCAL.segment,
                "M=D"
        );

        // goto functionName
        this.write("@" + functionName, "0;JMP");

        // write return label
        this.write(wrapLabel(returnLabel));
    }

    public void writeReturn() throws IOException {
        this.write(
                "@LCL",
                "D=M",
                "@R13", // R13 is a temporary variable to store the LCL (frame pointer)
                "M=D",
                "@5",
                "D=A",
                "@R13",
                "A=M-D", // A points to the return address
                "D=M", // D now contains the return address
                "@R14", // R14 is a temporary variable to store the return address
                "M=D" // store return address in R14
        );

        // pop the return value from the stack
        this.writeStackPop(true);
        this.write(
                "@ARG",
                "A=M",
                "M=D" // store the return value in ARG
        );

        this.write(
                "@ARG",
                "D=M+1", // D = ARG + 1
                "@SP",
                "M=D" // set SP to ARG + 1
        );

        // restore THAT, THIS, ARG, LCL
        this.write(
                "@R13",
                "M=M-1", // decrement frame pointer
                "A=M",
                "D=M", // D = LCL
                "@THAT",
                "M=D", // restore THAT
                "@R13",
                "M=M-1", // decrement frame pointer
                "A=M",
                "D=M", // D = LCL
                "@THIS",
                "M=D", // restore THIS
                "@R13",
                "M=M-1", // decrement frame pointer
                "A=M",
                "D=M", // D = LCL
                "@ARG",
                "M=D", // restore ARG
                "@R13",
                "M=M-1", // decrement frame pointer
                "A=M",
                "D=M", // D = LCL
                "@LCL",
                "M=D" // restore LCL
        );
        // goto return address
        this.write(
                "@R14",
                "A=M", // A points to the return address
                "0;JMP" // jump to the return address
        );
    }

    private String createEqualityCommands(ArithmeticCommand arithmeticCommand) {
        String eqLabel = createAutoLabel("EQ");
        String afterLabel = createAutoLabel("AFTER_EQ");
        StringJoiner joiner = new StringJoiner("\n");
        return joiner
                .add(arithmeticCommand.arithmeticTranslation)
                .add("@" + eqLabel)
                .add("D;" + arithmeticCommand.jumpTranslation)
                .add("D=0")
                .add("@" + afterLabel)
                .add("0;JMP")
                .add(wrapLabel(eqLabel))
                .add("D=-1")
                .add(wrapLabel(afterLabel))
                .toString();
    }

    private String createAutoLabel(String label) {
        return label + "." + AUTO_BRANCH_INDEX++;
    }

    private String generateFunctionEntryLabel(String functionName) {
        return this.fileName + "." + functionName;
    }

    private String createReturnLabel(String functionName) {
        return this.generateFunctionEntryLabel(functionName) + "$ret." + RETURN_INDEX++;
    }

    private String wrapLabel(String label) {
        return "(" + label + ")";
    }

    private void writeStackPush() throws IOException {
        this.write(
                "@SP",
                "A=M",
                "M=D",
                "@SP",
                "M=M+1"
        );
    }

    // pops 1 off the stack. Will store in D if storeInD is true. It will always be stored in M
    private void writeStackPop(boolean storeInD) throws IOException {
        this.write(
                "@SP",
                "M=M-1",
                "A=M",
                storeInD ? "D=M" : ""
        );
    }

    private void write(String... lines) throws IOException {
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            this.bufferedWriter.write(line);
            this.bufferedWriter.newLine();
        }
    }

    @Override
    public void close() throws IOException {
        if (!BOOTSTRAP) {
            this.write(
                    "(END)",
                    "@END",
                    "0;JMP"
            );
        }
        this.bufferedWriter.close();
    }

    /**
     * Points to the segment and index in the segment
     * Note: D should not be modified in this method unless storeInD is true
     *
     * @param segment  the segment to point to
     * @param index    if -1, it will point to the segment address instead of referencing it
     * @param storeInD if true, it will store the value in D
     */
    private void writePointToSegment(Segment segment, int index, boolean storeInD) throws IOException {
        this.write(
                segment.getSegmentAddress(this.fileName, index),
                segment.shouldReference() && index != -1 ? "A=M" : ""
        );
        if (segment.shouldReference() && index != -1) {
            for (int i = 0; i < index; i++) {
                this.write("A=A+1");
            }
        }
        if (storeInD) {
            this.write("D=M");
        }
    }

    public enum Segment {
        LOCAL("LCL"),
        ARGUMENT("ARG"),
        THIS("THIS"),
        THAT("THAT"),
        TEMP("R5"),
        POINTER,
        STATIC,
        CONSTANT;

        private final String segment;

        Segment() {
            this.segment = null;
        }

        Segment(String segment) {
            this.segment = segment;
        }

        public static Segment fromString(String segment) {
            return switch (segment) {
                case "local" -> LOCAL;
                case "argument" -> ARGUMENT;
                case "this" -> THIS;
                case "that" -> THAT;
                case "temp" -> TEMP;
                case "pointer" -> POINTER;
                case "static" -> STATIC;
                case "constant" -> CONSTANT;
                default -> throw new IllegalArgumentException("Unknown segment: " + segment);
            };
        }

        public String getSegmentAddress(String fileName, int index) {
            return switch (this) {
                case LOCAL, ARGUMENT, THIS, THAT -> "@" + segment;
                case TEMP -> "@R" + (5 + index);
                case POINTER -> switch (index) {
                    case 0 -> "@" + THIS.segment;
                    case 1 -> "@" + THAT.segment;
                    default -> throw new IllegalStateException("Unexpected value: " + index);
                };
                case STATIC -> "@" + fileName + "." + index;
                case CONSTANT -> throw new IllegalStateException("Constant segment should not be used in this context");
            };
        }

        public boolean shouldReference() {
            return switch (this) {
                case POINTER, STATIC, TEMP -> false;
                case CONSTANT -> throw new IllegalStateException("Constant segment should not be used in this context");
                default -> true;
            };
        }
    }

    public enum ArithmeticCommand {
        ADD("D=D+M"),
        SUB("D=M-D"),
        AND("D=D&M"),
        OR("D=D|M"),
        NEG("D=-M"),
        NOT("D=!M"),
        EQ("D=D-M", "JEQ"),
        GT("D=M-D", "JGT"),
        LT("D=M-D", "JLT");

        private final String arithmeticTranslation;
        private final String jumpTranslation;

        ArithmeticCommand(String arithmeticTranslation) {
            this.jumpTranslation = null;
            this.arithmeticTranslation = arithmeticTranslation;
        }

        ArithmeticCommand(String arithmeticTranslation, String jumpTranslation) {
            this.arithmeticTranslation = arithmeticTranslation;
            this.jumpTranslation = jumpTranslation;
        }

        public static ArithmeticCommand fromString(String command) {
            return switch (command) {
                case "add" -> ADD;
                case "sub" -> SUB;
                case "neg" -> NEG;
                case "eq" -> EQ;
                case "gt" -> GT;
                case "lt" -> LT;
                case "and" -> AND;
                case "or" -> OR;
                case "not" -> NOT;
                default -> throw new IllegalArgumentException("Unknown command: " + command);
            };
        }
    }
}
