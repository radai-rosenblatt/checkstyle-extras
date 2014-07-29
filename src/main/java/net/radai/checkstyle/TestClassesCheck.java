package net.radai.checkstyle;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.puppycrawl.tools.checkstyle.api.AbstractFileSetCheck;

/**
 * @author Radai Rosenblatt
 */
public class TestClassesCheck extends AbstractFileSetCheck {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([^;]+)\\s*;");
    private static final Pattern IGNORE_CLASS_PATTERN = Pattern.compile("^\\s*//\\s*NOTESTCLASS\\s*$");

    private boolean ignoreAnnotations = false;
    private boolean ignoreInterfaces = false;
    private boolean ignoreEnums = false;
    private boolean ignoreAbstracts = false;
    private Set<Pattern> excludePatterns = new HashSet<>();

    private Map<String, String> sourceToFile;
    private Set<String> sourceClasses;
    private Set<String> testClasses;

    public void setIgnoreAnnotations(boolean ignoreAnnotations) {
        this.ignoreAnnotations = ignoreAnnotations;
    }

    public void setIgnoreInterfaces(boolean ignoreInterfaces) {
        this.ignoreInterfaces = ignoreInterfaces;
    }

    public void setIgnoreEnums(boolean ignoreEnums) {
        this.ignoreEnums = ignoreEnums;
    }

    public void setIgnoreAbstracts(boolean ignoreAbstracts) {
        this.ignoreAbstracts = ignoreAbstracts;
    }

    public void setExcludePatterns(String excludePatterns) {
        String[] parts = excludePatterns.split("\\s*,\\s*");
        for (String part : parts) {
            Pattern pattern = Pattern.compile(part);
            this.excludePatterns.add(pattern);
        }
    }

    @Override
    public void beginProcessing(String aCharset) {
        super.beginProcessing(aCharset);
        sourceToFile = new HashMap<>();
        sourceClasses = new HashSet<>();
        testClasses = new HashSet<>();
    }

    @Override
    protected void processFiltered(File aFile, List<String> aLines) {
        String fileName = aFile.getName();
        if (!fileName.endsWith(".java") || fileName.contains("package-info")) {
            return;
        }

        String packageName = locatePackage(aLines);
        String className = fileName.substring(0, fileName.indexOf(".java"));
        String fqcn = packageName.isEmpty() ? className : packageName + "." + className;

        if (className.endsWith("Test") || className.startsWith("Test")) {
            testClasses.add(fqcn);
        } else if (!ignoreFile(aFile, fqcn, className, aLines)) {
            sourceClasses.add(fqcn);
            sourceToFile.put(fqcn, aFile.getPath());
        }
    }

    @Override
    public void finishProcessing() {
        super.finishProcessing();
        Set<String> possibleTestClassFqcns = new HashSet<>();
        for (String sourceClassFqcn : sourceClasses) {
            possibleTestClassFqcns.clear();
            int lastDotIndex = sourceClassFqcn.lastIndexOf(".");
            if (lastDotIndex < 0) {
                possibleTestClassFqcns.add(sourceClassFqcn + "Test");
                possibleTestClassFqcns.add("Test" + sourceClassFqcn);
            } else {
                String packageName = sourceClassFqcn.substring(0, lastDotIndex);
                String className = sourceClassFqcn.substring(lastDotIndex + 1);
                possibleTestClassFqcns.add(packageName + ".Test" + className);
                possibleTestClassFqcns.add(packageName + "." + className + "Test");
            }
            locateInTestClasses(sourceClassFqcn, possibleTestClassFqcns);
        }
    }

    private boolean ignoreFile(File aFile, String fqcn, String simpleClassName, List<String> lines) {
        for (Pattern excludePattern : excludePatterns) {
            if (excludePattern.matcher(fqcn).matches()) {
                return true;
            }
        }
        Pattern annotationPattern = Pattern.compile("^\\s*(public)?\\s*@interface\\s+"+simpleClassName);
        Pattern interfacePattern = Pattern.compile("^\\s*(public)?\\s*interface\\s+"+simpleClassName);
        Pattern enumPattern = Pattern.compile("^\\s*(public)?\\s*enum\\s+"+simpleClassName);
        Pattern abstractPattern = Pattern.compile("^\\s*(public)?\\s*abstract\\s*class\\s+"+simpleClassName);
        for (String line : lines) {
            if (ignoreAnnotations && annotationPattern.matcher(line).find()) {
                return true;
            }
            if (ignoreInterfaces && interfacePattern.matcher(line).find()) {
                return true;
            }
            if (ignoreEnums && enumPattern.matcher(line).find()) {
                return true;
            }
            if (ignoreAbstracts && abstractPattern.matcher(line).find()) {
                return true;
            }
            if (IGNORE_CLASS_PATTERN.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    private String locatePackage(List<String> lines) {
        // this is good enough until i see a multi-line package statement ...
        for (String line : lines) {
            Matcher matcher = PACKAGE_PATTERN.matcher(line);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return ""; // default package
    }

    private void locateInTestClasses(String className, Iterable<String> testClassCandidates) {
        for (String candidate : testClassCandidates) {
            if (testClasses.contains(candidate)) {
                return;
            }
        }
        log(1, "class " + className + " has no test class(es)");
        //we need to write out the message we just created
        //ourselves because Checker wont do it for us
        fireErrors(sourceToFile.get(className));
    }

}
