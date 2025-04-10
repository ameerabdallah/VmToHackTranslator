import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class CodeWriter implements AutoCloseable {

    private static final String R13 = "@R13";
    private static final String R14 = "@R14";
    private static final String R15 = "@R15";
    private final BufferedWriter bufferedWriter;
    private String fileName;

    public CodeWriter(OutputStream out) {
        this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(out));
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void writeComment(String comment) throws IOException {
        this.bufferedWriter.write("// " + comment);
        this.bufferedWriter.newLine();
    }

    /**
     * Pops the top two values from the stack, performs the operation, and pushes the result back onto the stack.
     */
    public void writeArithmetic(String command) throws IOException {
        writeStackPop(R13);
        writeStackPop(R14);

        // fill D and M registers
        this.bufferedWriter.write(R13);
        this.bufferedWriter.newLine();
        this.bufferedWriter.write("D=M");
        this.bufferedWriter.newLine();
        this.bufferedWriter.write(R14);
        this.bufferedWriter.newLine();

        String asmCommand = switch (command) {
            case "add" -> "D=D+M";
            case "sub" -> "D=M-D";
            case "neg" -> "D=-M";
            case "eq" -> "D=D-M\n@SP\nA=M-1\nM=D;JEQ";
            case "gt" -> "D=M-D\n@SP\nA=M-1\nM=D;JGT";
            case "lt" -> "D=M-D\n@SP\nA=M-1\nM=D;JLT";
            case "and" -> "D=D&M";
            case "or" -> "D=D|M";
            case "not" -> "D=!M";
            default -> throw new IllegalStateException("Unexpected value: " + command);
        };

        this.bufferedWriter.write(asmCommand);
        this.bufferedWriter.newLine();
        writeStackPush();
    }

    private void writeStackPush() throws IOException {
        this.bufferedWriter.write("@SP");
        this.bufferedWriter.newLine();
        this.bufferedWriter.write("A=M");
        this.bufferedWriter.newLine();
        this.bufferedWriter.write("M=D");
        this.bufferedWriter.newLine();
        this.bufferedWriter.write("@SP");
        this.bufferedWriter.newLine();
        this.bufferedWriter.write("M=M+1");
        this.bufferedWriter.newLine();
    }

    private void writeStackPop() throws IOException {
        this.bufferedWriter.write("@SP");
        this.bufferedWriter.newLine();
        this.bufferedWriter.write("M=M-1");
        this.bufferedWriter.newLine();
    }

    private void writeStackPop(String where) throws IOException {
        this.bufferedWriter.write("@SP");
        this.bufferedWriter.newLine();
        this.bufferedWriter.write("A=M-1");
        this.bufferedWriter.newLine();
        this.bufferedWriter.write("D=M");
        this.bufferedWriter.newLine();
        this.bufferedWriter.write(where);
        this.bufferedWriter.newLine();
        this.bufferedWriter.write("M=D");
        this.bufferedWriter.newLine();
        this.bufferedWriter.write("@SP");
        this.bufferedWriter.newLine();
        this.bufferedWriter.write("M=M-1");
        this.bufferedWriter.newLine();
    }

    public void writePushPop(Parser.CommandType command, String segment, int index) throws IOException {

        switch (command) {
            case C_PUSH -> {
                switch (segment) {
                    case "temp" -> {
                        this.bufferedWriter.write("@R" + (5 + index));
                        this.bufferedWriter.newLine();
                        this.bufferedWriter.write("D=M");
                    }
                    case "static" -> {
                        this.bufferedWriter.write("@" + fileName + "." + index);
                        this.bufferedWriter.newLine();
                        this.bufferedWriter.write("D=M");
                    }
                    case "constant" -> {
                        this.bufferedWriter.write("@" + index);
                        this.bufferedWriter.newLine();
                        this.bufferedWriter.write("D=A");
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + segment);
                }
                this.bufferedWriter.newLine();
                writeStackPush();
            }
            case C_POP -> {
            }
            default -> throw new IllegalArgumentException("Unknown command type: " + command);
        }
    }

    @Override
    public void close() throws IOException {
        // create infinite loop at the end of the program before closing the file
        this.bufferedWriter.write("(END)\n@END\n0;JMP");
        this.bufferedWriter.close();
    }
}
