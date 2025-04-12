import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.StringJoiner;

public class CodeWriter implements AutoCloseable {

    private static final String R13 = "@R13";
    private static final String R14 = "@R14";
    private static final String R15 = "@R15";
    private static int AUTO_BRANCH_INDEX = 0;
    private final BufferedWriter bufferedWriter;
    private String fileName;

    public CodeWriter(OutputStream out) {
        this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(out));
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void writeComment(String comment) throws IOException {
        this.bufferedWriter.newLine();
        this.write("// " + comment);
    }

    /**
     * Pops the top two values from the stack, performs the operation, and pushes the result back onto the stack.
     */
    public void writeArithmetic(String command) throws IOException {
        switch (command) {
            case "add", "sub", "eq", "gt", "lt", "and", "or" -> {
                writeStackPop2();
            }
            case "neg", "not" -> {
                writeStackPop(true);
            }
            default -> throw new IllegalStateException("Unexpected value: " + command);
        }

        String asmCommands = switch (command) {
            case "add" -> "D=D+M";
            case "sub" -> "D=M-D";
            case "lt" -> createEqualityCommands(EqualityType.LESS_THAN);
            case "eq" -> createEqualityCommands(EqualityType.EQUAL);
            case "gt" -> createEqualityCommands(EqualityType.GREATER_THAN);
            case "and" -> "D=D&M";
            case "or" -> "D=D|M";
            case "neg" -> "D=-M";
            case "not" -> "D=!M";
            default -> throw new IllegalStateException("Unexpected value: " + command);
        };

        // consistent line breaks
        this.write(asmCommands.split("\n"));

        // push D onto the stack after performing arithmetic
        this.writeStackPush();
    }

    private String createEqualityCommands(EqualityType equalityType) throws IOException {
        String eqLabel = createAutoLabel("EQ");
        String afterLabel = createAutoLabel("AFTER_EQ");
        StringJoiner joiner = new StringJoiner("\n");
        return joiner
                .add(equalityType.getArithmeticType())
                .add("@" + eqLabel)
                .add("D;" + equalityType.getJumpType())
                .add("D=0")
                .add("@" + afterLabel)
                .add("0;JMP")
                .add(wrapLabel(eqLabel))
                .add("D=-1")
                .add(wrapLabel(afterLabel))
                .toString();
    }

    private String createAutoLabel(String label) throws IOException {
        return label + "." + AUTO_BRANCH_INDEX++;
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

    // pops 2 off the stack and stores them in D and M. if stack is ordered like: X, Y <-SP. Then D will equal Y and
    // M will equal X
    private void writeStackPop2() throws IOException {
        this.writeStackPop(true);
        this.writeStackPop(false);
    }

    private void writeStackPop(boolean storeInD) throws IOException {
        this.writeStackPop("@SP", storeInD);
    }

    // pops 1 off the stack. Will store in D if storeInD is true. It will always be stored in M
    private void writeStackPop(String where, boolean storeInD) throws IOException {
        this.write(
                where,
                "M=M-1",
                "A=M",
                storeInD ? "D=M" : ""
        );
    }

    public void writePushPop(Parser.CommandType command, String segment, int index) throws IOException {
        switch (command) {
            case C_PUSH -> {
                if (segment.equals("constant")) {
                    this.write(
                            "@" + index,
                            "D=A"
                    );
                } else {
                    pointToSegment(segment, index, true);
                }
                writeStackPush();
            }
            case C_POP -> {
                writeStackPop(true);
                pointToSegment(segment, index, false);
                this.write("M=D");
            }
            default -> throw new IllegalArgumentException("Unknown command type: " + command);
        }
    }

    // D should never be changed by this method unless storeInD is true
    private void pointToSegment(String segment, int index, boolean storeInD) throws IOException {
        String segmentAddress = switch (segment) {
            case "static" -> "@" + fileName + "." + index;
            case "temp" -> "@R" + (5 + index);
            case "local" -> "@LCL";
            case "argument" -> "@ARG";
            case "this" -> "@THIS";
            case "that" -> "@THAT";
            case "pointer" -> switch (index) {
                case 0 -> "@THIS";
                case 1 -> "@THAT";
                default -> throw new IllegalStateException("Unexpected value: " + index);
            };
            default -> throw new IllegalStateException("Unexpected value: " + segment);
        };

        this.write(
                segmentAddress,
                !segment.equals("pointer") && !segment.equals("static") ? "A=M" : ""
        );
        switch (segment) {
            case "local", "argument", "this", "that" -> {
                for (int i = 0; i < index; i++) {
                    this.write("A=A+1");
                }
            }
        }
        if (storeInD) {
            this.write("D=M");
        }
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
        // create infinite loop at the end of the program before closing the file
        this.bufferedWriter.write("(END)\n@END\n0;JMP");
        this.bufferedWriter.close();
    }

    public enum EqualityType {
        EQUAL("JEQ", "D=D-M"),
        GREATER_THAN("JGT", "D=M-D"),
        LESS_THAN("JLT", "D=M-D");

        private final String jumpType;
        private final String arithmeticType;

        EqualityType(String jumpType, String arithmeticType) {
            this.jumpType = jumpType;
            this.arithmeticType = arithmeticType;
        }

        public String getJumpType() {
            return jumpType;
        }

        public String getArithmeticType() {
            return arithmeticType;
        }
    }
}
