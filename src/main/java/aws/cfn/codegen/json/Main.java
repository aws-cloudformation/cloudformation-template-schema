package aws.cfn.codegen.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Main {

    @Option(name = "--cfn-spec-url",
        usage = "URL location of the CFN Resource specification see locations at " +
            "https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-resource-specification.html")
    private URI location;

    @Option(name = "--aws-region",
        usage = "AWS Region this resource specification belongs to e.g. us-east-2, See regions at " +
            "https://docs.aws.amazon.com/general/latest/gr/rande.html")
    private String region;

    @Option(name = "--json-schema-version", usage = "Support values are [draft04, draft07]")
    private SchemaDraft draft;

    @Option(name = "--output-dir",
        usage = "output directory where the schemas will be generated. e.g. /tmp, schemas will be /tmp/<region>/*-spec.json")
    private File outputDir;

    @Option(name = "--config-file",
            usage = "configuration file for specifying groups. See sample config.yml included")
    private File configFile;

    @Option(name = "--single",
            usage = "Use this flag is you are generating this for single resource")
    private Boolean single;

    public enum MergeType {
        merge,
        override
    }

    @Option(name = "--merge",
            usage = "merge with the default configuration that we have")
    private Map<String, MergeType> merges = Maps.asMap(
        Sets.newHashSet("settings", "specifications", "groups"),
        s -> MergeType.merge
    );

    private Main() {}

    private Config handleBundledConfig(Config config,
                                       ClassLoader loader,
                                       ObjectMapper mapper) throws IOException {
        Config bundled =
            mapper.readValue(loader.getResource("config.yml"), Config.class);
        if (config != null) {
            config.setSettings(
                Optional.ofNullable(merges.get("settings"))
                    .map(type -> {
                        switch (type) {
                            case merge:
                                Config.Settings fromCfgFile = config.getSettings();
                                Config.Settings bundledCfg = bundled.getSettings();
                                return bundledCfg.mergeOverride(fromCfgFile);

                            case override:
                            default:
                                return config.getSettings();
                        }
                    })
                    .orElseGet(config::getSettings)
            );
            config.setGroups(
                Optional.ofNullable(merges.get("groups"))
                    .map(type -> {
                        switch (type) {
                            case merge:
                                Map<String, GroupSpec> fromCfgFile = config.getGroups();
                                Map<String, GroupSpec> fromBundled = bundled.getGroups();
                                fromBundled.putAll(fromCfgFile);
                                return fromBundled;

                            case override:
                            default:
                                return config.getGroups();
                        }
                    }).orElseGet(config::getGroups)
            );
            config.setSpecifications(
                Optional.ofNullable(merges.get("specifications"))
                    .map(type -> {
                        switch (type) {
                            case merge:
                                Map<String, URI> fromCfnFile = config.getSpecifications();
                                Map<String, URI> fromBundled = bundled.getSpecifications();
                                fromBundled.putAll(fromCfnFile);
                                return fromBundled;

                            case override:
                            default:
                                return config.getSpecifications();
                        }
                    })
                    .orElseGet(config::getSpecifications)
            );
            return config;
        }
        return bundled;
    }

    private void handleCmdLineOverrides(Config config) {
        if (region != null) {
            if (location != null) {
                config.getSpecifications().put(region, location);
            }
            config.getSettings().getRegions().add(region);
        }

        if (draft != null) {
            config.getSettings().setDraft(draft);
        }

        if (single != null) {
            config.getSettings().setSingle(single);
        }

        if (outputDir != null) {
            config.getSettings().setOutput(outputDir);
        }
    }

    private void generate(Config config) throws Exception {
        Set<String> regions = config.getSpecifications().keySet();
        Set<String> selectedRegions = new HashSet<>(regions.size());
        config.getSettings().getRegions()
            .forEach(prefix -> {
                for (String each: regions) {
                    if (each.startsWith(prefix)) {
                        selectedRegions.add(each);
                    }
                }
            });
        Publisher publisher = new aws.cfn.codegen.json.github.Publisher(new File(""));
        for (String each: selectedRegions) {
            new Codegen(config, each).generate(publisher);
        }
    }

    private void copy(Config config) {
        File output =  config.getSettings().getOutput();
        File input = new File("src/main/resources");
        Path inPath = input.toPath();
        Path outPathDir = output.toPath();
        Arrays.stream(input.listFiles(
            (dir, name) -> name.endsWith(".json")
        )).map(File::toPath)
            .forEach(s -> {
                try {
                    Path out = outPathDir.resolve(s.getFileName());
                    Files.copy(s, out, StandardCopyOption.REPLACE_EXISTING);
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    private void execute() throws Exception {
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.enable(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        loader = loader == null ? getClass().getClassLoader() : loader;
        Config config = configFile != null ?
            mapper.readValue(configFile, Config.class) : null;
       config = handleBundledConfig(config, loader, mapper);
       handleCmdLineOverrides(config);
       copy(config);
       generate(config);
    }

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        CmdLineParser parser = new CmdLineParser(main);
        parser.parseArgument(args);
        main.execute();
    }
}
