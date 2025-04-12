import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class VMTranslator {
    private static void usage() {
        System.out.printf("Usage: java %s <inputfile[.vm] | directory>", VMTranslator.class.getName());
        System.exit(1);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            usage();
            return;
        }

        // check if it is a directory
        File file = new File(args[0]);

        String[] inputFileNames;
        if (file.isDirectory()) {
            inputFileNames = file.list((dir, name) -> name.endsWith(".vm"));
            if (inputFileNames == null || inputFileNames.length == 0) {
                System.out.println("No .vm files found in the directory.");
                usage();
                return;
            }
        } else if (file.isFile() && file.getName().endsWith(".vm")) {
            inputFileNames = new String[]{file.getName()};
        } else {
            usage();
            return; // This line should not be reached due to the usage() method
        }

        String outputFileName = file.getAbsolutePath() + File.separator + (file.isDirectory() ? file.getName() +
                ".asm" : file.getName().substring(
                0,
                file.getName().length() - 3
        ) + ".asm");
        try (CodeWriter codeWriter =
                     new CodeWriter(new FileOutputStream(outputFileName)
                     )) {
            // Bootstrap code
            for (String inputFileName : inputFileNames) {

                String inputFilePath = file.isDirectory() ?
                        file.getAbsolutePath() + File.separator + inputFileName : file.getAbsolutePath();
                try (Parser parser = new Parser(new FileInputStream(inputFilePath))) {
                    String inputFileNameWithoutExtension = inputFileName.substring(
                            0,
                            inputFileName.length() - 3
                    );
                    codeWriter.setFileName(inputFileNameWithoutExtension);
                    while (parser.hasMoreLines()) {
                        parser.advance();
                        codeWriter.writeComment(parser.getCurrentLine());
                        switch (parser.commandType()) {
                            case C_ARITHMETIC -> codeWriter.writeArithmetic(parser.arg1());
                            case C_PUSH, C_POP ->
                                    codeWriter.writePushPop(parser.commandType(), parser.arg1(), parser.arg2());
                            case C_LABEL -> codeWriter.writeLabel(parser.arg1());
                            case C_GOTO -> codeWriter.writeGoto(parser.arg1());
                            case C_IF -> codeWriter.writeIf(parser.arg1());
                            case C_FUNCTION -> codeWriter.writeFunction(parser.arg1(), parser.arg2());
                            case C_RETURN -> codeWriter.writeReturn();
                            case C_CALL -> codeWriter.writeCall(parser.arg1(), parser.arg2());
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
