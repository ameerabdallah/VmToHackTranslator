import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

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
            inputFileNames = new String[]{ file.getName() };
        } else {
            usage();
            return; // This line will never be reached, but it's good practice to include it.
        }

        Arrays
                .stream(inputFileNames)
                .parallel() // might as well use parallel processing
                .forEach(inputFileName -> {
            String inputFilePath = file.isDirectory() ? file.getAbsolutePath() + File.separator + inputFileName : file.getAbsolutePath();
            String inputFileNameWithoutExtension = inputFileName.substring(0, inputFileName.length() - 3);
            String outputFilePath = inputFilePath.substring(0, inputFilePath.length() - 3) + ".asm";

            try (Parser parser = new Parser(new FileInputStream(inputFilePath))) {
                try (CodeWriter codeWriter = new CodeWriter(new FileOutputStream(outputFilePath))) {
                    codeWriter.setFileName(inputFileNameWithoutExtension);
                    while (parser.hasMoreLines()) {
                        parser.advance();
                        codeWriter.writeComment(parser.getCurrentLine());
                        if (parser.commandType().isArithmetic()) {
                            codeWriter.writeArithmetic(parser.arg1());
                        } else if (parser.commandType().isPushPop()) {
                            codeWriter.writePushPop(parser.commandType(), parser.arg1(), parser.arg2());
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
