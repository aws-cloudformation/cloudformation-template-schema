package aws.cfn.codegen.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Sets;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.net.URI;
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

    @Option(name = "--merge",
            usage = "merge with the default configuration that we have")
    private boolean merge = true;

    private Main() {}

    private void execute() throws Exception {
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        loader = loader == null ? getClass().getClassLoader() : loader;
        Config config = configFile != null ?
            mapper.readValue(configFile, Config.class) :
            mapper.readValue(loader.getResource("config.yml"), Config.class);

        if (merge && configFile != null) {
            Config bundled =  mapper.readValue(loader.getResource("config.yml"), Config.class);
            Config.Builder builder = Config.builder(bundled);
            builder.mergeOverride(config);
            config = builder.build();
        }

        // are their overriding args sent in outside config file
        Config.Settings settings = config.getSettings();
        Set<String> regions = this.region != null ? Sets.newHashSet(this.region) : settings.getRegions();
        File outputDir = this.outputDir != null ? this.outputDir : settings.getOutput();
        SchemaDraft draft = this.draft != null ? this.draft : settings.getDraft();
        boolean single = this.single != null ? this.single : settings.getSingle();

        config = Config.builder(config)
            .withJsonSchema(draft)
            .withOutputDirectory(outputDir)
            .setRegions(regions)
            .isSingleResourceSpec(single)
            .build();

        new Codegen(config).generate();
    }

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        CmdLineParser parser = new CmdLineParser(main);

        parser.parseArgument(args);
        main.execute();
    }
}
