package io.github.eutro.wasm2j;

import io.github.eutro.wasm2j.bits.FormatDetector;
import io.github.eutro.wasm2j.bits.InterfaceBasedLinker;
import io.github.eutro.wasm2j.bits.NameSectionParser;
import io.github.eutro.wasm2j.bits.OutputsToDirectory;
import io.github.eutro.wasm2j.support.CaseStyle;
import io.github.eutro.wasm2j.support.NameMangler;
import io.github.eutro.wasm2j.support.NameSupplier;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.github.eutro.wasm2j.support.NameMangler.IllegalTokenPolicy.MANGLE_BIJECTIVE;

public class Cli {
    public static void main(String[] args) {
        List<String> paths = new ArrayList<>();
        boolean setOutput = false;
        File outputDir = new File(".");
        boolean suppressFlags = false;
        String pkgName = "";
        for (int i = 0; i < args.length; ) {
            String arg = args[i++];
            if (!suppressFlags && arg.startsWith("-")) {
                switch (arg) {
                    case "-h":
                    case "--help":
                        printHelp();
                        return;
                    case "-o":
                    case "--output":
                        if (i == args.length) {
                            System.err.printf("%s: expected file", arg);
                            System.exit(1);
                        }
                        if (setOutput) {
                            System.err.printf("%s: output already specified", arg);
                            System.exit(1);
                        }
                        setOutput = true;
                        outputDir = new File(args[i++]);
                        break;
                    case "--":
                        suppressFlags = true;
                        break;
                    default:
                        System.err.printf("%s: unknown flag", arg);
                        System.exit(1);
                }
                continue;
            }
            paths.add(arg);
        }
        if (paths.isEmpty()) {
            printHelp();
            System.exit(1);
        }

        WasmCompiler cc = new WasmCompiler();
        FormatDetector fd = cc.add(FormatDetector.BIT);
        cc.add(new NameSectionParser<>());
        CaseStyle.Detect sourceStyle = new CaseStyle.Detect(CaseStyle.LOWER_SNAKE);
        InterfaceBasedLinker<WasmCompiler> linker = cc.add(new InterfaceBasedLinker<>(NameSupplier.createSimple(
                pkgName,
                NameMangler.javaIdent(MANGLE_BIJECTIVE),
                sourceStyle, CaseStyle.UPPER_CAMEL,
                sourceStyle, CaseStyle.LOWER_CAMEL
        )));
        new OutputsToDirectory<>(outputDir.toPath()).addTo(linker);
        for (String spec : paths) {
            String[] fileAndModules = spec.split(":");
            if (fileAndModules.length == 0) {
                System.err.printf("invalid file specification: \"%s\"%n", spec);
                System.exit(1);
            }
            File file = new File(fileAndModules[0]);
            String name = file.getName();
            if (name.endsWith(".wasm")) name = name.substring(0, name.length() - ".wasm".length());
            else if (name.endsWith(".wat")) name = name.substring(0, name.length() - ".wat".length());
            try {
                ModuleCompilation comp = fd.submitFile(file.toPath());
                comp.setName(pkgName +
                        sourceStyle.convertTo(
                                CaseStyle.UPPER_CAMEL,
                                name));
                for (int i = 1; i < fileAndModules.length; i++) {
                    linker.register(fileAndModules[i], comp.node);
                }
                comp.run();
            } catch (IOException e) {
                System.err.printf("could not read file %s: %s%n", file, e);
                System.exit(1);
            }
        }
        linker.finish();
    }

    private static void printHelp() {
        System.out.println(
                "usage: wasm2j [-h|--help] [-o|--output <dir>] [-p|--package <package>] <file>(:<module>)* ...\n" +
                        "\n" +
                        "  <file>(:<module>)* : each file can be specified to implement a number of modules\n" +
                        "                       importable by others, separated by colons\n" +
                        "  -o|--output <dir> : output classes to <dir>/name/of/package/ModuleName.class\n" +
                        "  -p|--package <package> : set name/of/package to <package>\n" +
                        "  -h|--help : show this help"
        );
    }
}
