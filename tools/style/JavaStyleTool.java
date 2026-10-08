import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.JavaFormatterOptions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class JavaStyleTool {
    private final JavaParser parser;
    private final Formatter formatter;

    public JavaStyleTool() {
        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        parser = new JavaParser(configuration);
        formatter =
                new Formatter(
                        JavaFormatterOptions.builder()
                                .style(JavaFormatterOptions.Style.AOSP)
                                .build());
    }

    public static void main(String[] arguments) throws Exception {
        JavaStyleTool tool = new JavaStyleTool();
        tool.run(Path.of(arguments[0]).toAbsolutePath().normalize(), arguments.length > 1);
    }

    public void run(Path root, boolean check) throws Exception {
        List<Path> changed = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                Path relative = root.relativize(path);
                if (!path.toString().endsWith(".java") || excluded(relative)) {
                    continue;
                }
                String source = Files.readString(path, StandardCharsets.UTF_8);
                String formatted = format(source);
                if (!source.equals(formatted)) {
                    changed.add(relative);
                    if (!check) {
                        Files.writeString(path, formatted, StandardCharsets.UTF_8);
                    }
                }
            }
        }
        for (Path path : changed) {
            System.out.println(path);
        }
        System.out.println((check ? "Style differences: " : "Formatted files: ") + changed.size());
        if (check && !changed.isEmpty()) {
            throw new IllegalStateException(
                    "Run scripts/Format-Java.ps1 to apply the project style");
        }
    }

    private boolean excluded(Path relative) {
        for (Path part : relative) {
            if (List.of("target", "work", "dist", ".git", ".reference", ".style-cache")
                    .contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private CompilationUnit parse(String source) {
        var result = parser.parse(source);
        if (!result.isSuccessful()) {
            throw new IllegalArgumentException(result.getProblems().toString());
        }
        return result.getResult().orElseThrow();
    }

    private String format(String source) throws Exception {
        CompilationUnit unit = parse(splitFields(source));
        LexicalPreservingPrinter.setup(unit);
        for (Comment comment : new ArrayList<>(unit.getAllContainedComments())) {
            comment.remove();
        }
        unit.removeComment();
        for (IfStmt statement : unit.findAll(IfStmt.class)) {
            statement.setThenStmt(block(statement.getThenStmt()));
            statement
                    .getElseStmt()
                    .filter(value -> !(value instanceof IfStmt))
                    .ifPresent(value -> statement.setElseStmt(block(value)));
        }
        for (ForStmt statement : unit.findAll(ForStmt.class)) {
            statement.setBody(block(statement.getBody()));
        }
        for (ForEachStmt statement : unit.findAll(ForEachStmt.class)) {
            statement.setBody(block(statement.getBody()));
        }
        for (WhileStmt statement : unit.findAll(WhileStmt.class)) {
            statement.setBody(block(statement.getBody()));
        }
        for (DoStmt statement : unit.findAll(DoStmt.class)) {
            statement.setBody(block(statement.getBody()));
        }
        String formatted =
                formatter.formatSourceAndFixImports(LexicalPreservingPrinter.print(unit));
        for (int pass = 0; pass < 3; pass++) {
            String next = formatter.formatSourceAndFixImports(formatted);
            if (next.equals(formatted)) {
                break;
            }
            formatted = next;
        }
        return expandEmptyBlocks(formatted);
    }

    private String splitFields(String source) {
        CompilationUnit unit = parse(source);
        List<FieldDeclaration> fields =
                unit.findAll(FieldDeclaration.class).stream()
                        .filter(field -> field.getVariables().size() > 1)
                        .sorted(
                                Comparator.comparing(
                                                (FieldDeclaration field) ->
                                                        field.getBegin().orElseThrow())
                                        .reversed())
                        .toList();
        StringBuilder result = new StringBuilder(source);
        for (FieldDeclaration field : fields) {
            StringBuilder replacement = new StringBuilder();
            for (var variable : field.getVariables()) {
                FieldDeclaration declaration = field.clone();
                declaration.setVariables(
                        new com.github.javaparser.ast.NodeList<>(variable.clone()));
                replacement.append(declaration).append('\n');
            }
            var range = field.getRange().orElseThrow();
            result.replace(
                    offset(source, range.begin.line, range.begin.column),
                    offset(source, range.end.line, range.end.column) + 1,
                    replacement.toString());
        }
        return result.toString();
    }

    private Statement block(Statement statement) {
        if (statement instanceof BlockStmt) {
            return statement;
        }
        return new BlockStmt().addStatement(statement.clone());
    }

    private String expandEmptyBlocks(String source) {
        CompilationUnit unit = parse(source);
        List<Node> empty =
                new ArrayList<>(
                        unit.findAll(BlockStmt.class).stream()
                                .filter(block -> block.getStatements().isEmpty())
                                .toList());
        empty.addAll(
                unit.findAll(TypeDeclaration.class).stream()
                        .filter(type -> type.getMembers().isEmpty())
                        .toList());
        empty.addAll(
                unit.findAll(ObjectCreationExpr.class).stream()
                        .filter(
                                expression ->
                                        expression
                                                .getAnonymousClassBody()
                                                .map(List::isEmpty)
                                                .orElse(false))
                        .toList());
        List<Integer> offsets = new ArrayList<>();
        java.util.Map<Integer, String> indentationByOffset = new java.util.HashMap<>();
        for (Node node : empty) {
            var range = node.getRange().orElseThrow();
            int start = offset(source, range.begin.line, range.begin.column);
            int end = offset(source, range.end.line, range.end.column);
            int opening = source.lastIndexOf('{', end);
            if (opening >= start && source.substring(opening + 1, end).isBlank()) {
                offsets.add(opening);
                Node indentationNode =
                        node instanceof BlockStmt ? node.getParentNode().orElse(node) : node;
                var beginning = indentationNode.getBegin().orElseThrow();
                int indentationStart = offset(source, beginning.line, 1);
                int indentationEnd = indentationStart;
                while (indentationEnd < source.length() && source.charAt(indentationEnd) == ' ') {
                    indentationEnd++;
                }
                indentationByOffset.put(
                        opening, source.substring(indentationStart, indentationEnd));
            }
        }
        StringBuilder result = new StringBuilder(source);
        for (int opening : offsets.stream().distinct().sorted(Comparator.reverseOrder()).toList()) {
            int closing = source.indexOf('}', opening);
            result.replace(opening + 1, closing, "\n" + indentationByOffset.get(opening));
        }
        return result.toString();
    }

    private int offset(String source, int line, int column) {
        int start = 0;
        for (int current = 1; current < line; current++) {
            start = source.indexOf('\n', start) + 1;
        }
        return start + column - 1;
    }
}
