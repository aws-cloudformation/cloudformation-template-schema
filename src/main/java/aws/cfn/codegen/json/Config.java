package aws.cfn.codegen.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.File;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * This represent the configuration that dictate {@link Codegen} to determine specification
 * by region and generate appropriate template schemas based on the groups that we specified
 * By default if the tool is not provided with a configuration, it loads the default configuration
 * file that is bundled with that package. See config.yml
 *
 * @see GroupSpec
 */
@lombok.Getter
@lombok.ToString
@lombok.EqualsAndHashCode
public final class Config {

    public static Builder builder() {
        return new Builder(null);
    }

    public static Builder builder(Config existing) {
        return new Builder(Objects.requireNonNull(existing));
    }

    public static class Builder {
        private SchemaDraft draft = SchemaDraft.draft07;
        private File outputDir;
        private String region;
        private boolean singleResourceSpec = false;
        private final Map<String, URI> regions = new LinkedHashMap<>(12);
        private final Map<String, GroupSpec> groups = new LinkedHashMap<>(5);

        private Builder(Config existing) {
            if (existing != null) {
                mergeOverride(existing);
            }
        }

        public Builder mergeOverride(Config other) {
            Objects.requireNonNull(other);
            Settings settings = other.getSettings();
            if (settings != null) {
                this.draft = settings.getDraft();
                this.outputDir = settings.getOutput();
                this.region = settings.getRegion();
                this.singleResourceSpec = settings.getSingle();
            }
            this.regions.putAll(other.getSpecifications());
            this.groups.putAll(other.getGroups());
            return this;
        }

        public Builder withJsonSchema(SchemaDraft draft) {
            this.draft = draft;
            return this;
        }

        public Builder withRegionSpec(String region, String location) {
            return withRegionSpec(region, URI.create(location));
        }

        public Builder withRegionSpec(String region, URI location) {
            regions.put(
                Objects.requireNonNull(region),
                location);
            return this;
        }

        public Builder withRegion(String region) {
            this.region = region;
            return this;
        }

        public Builder withOutputDirectory(File dir) {
            this.outputDir = Objects.requireNonNull(dir);
            return this;
        }

        public Builder isSingleResourceSpec(boolean single) {
            this.singleResourceSpec = single;
            return this;
        }

        public Builder withGroup(String grpName, GroupSpec spec) {
            groups.put(grpName, spec);
            return this;
        }

        public Config build() {
            return new Config(
                regions,
                new Settings(
                    draft,
                    region,
                    outputDir,
                    singleResourceSpec
                ),
                groups
            );
        }

    }

    @lombok.Getter
    @lombok.ToString
    @lombok.EqualsAndHashCode
    public static class Settings {
        private final SchemaDraft draft;
        private final String region;
        private final File output;
        private final Boolean single;

        @JsonCreator
        public Settings(@JsonProperty("draft") SchemaDraft draft,
                        @JsonProperty("region") String region,
                        @JsonProperty("output") File output,
                        @JsonProperty("single") Boolean single) {
            this.draft = draft;
            this.region = region == null ? "us-east-2" : region;
            this.output = Objects.requireNonNull(output);
            this.single = single == null ? false : single;
        }
    }

    private final Map<String, GroupSpec> groups;
    private final Settings settings;
    private final Map<String, URI> specifications;
    @JsonCreator
    public Config(@JsonProperty("specifications") final Map<String, URI> specifications,
                  @JsonProperty("settings") final Settings settings,
                  @JsonProperty("groups") final Map<String, GroupSpec> groups) {
        this.specifications = specifications != null ? specifications : new LinkedHashMap<>(1);
        this.settings = settings;
        this.groups = groups != null ? groups : new LinkedHashMap<>();
        validateRegionSpecs();
        validateGroups();
    }

    private void validateRegionSpecs() {
        if (specifications.isEmpty()) {
            specifications.put("us-east-2",
                URI.create("https://dnwj8swjjbsbt.cloudfront.net/latest/gzip/CloudFormationResourceSpecification.json"));
        }

        if (settings != null && !specifications.containsKey(settings.getRegion())) {
            throw new IllegalArgumentException("No region mapping was found " + settings.getRegion());
        }
    }

    private void validateGroups() {
        if (groups.isEmpty()) {
            GroupSpec spec = GroupSpec.includesOnly("all", "AWS.*");
            groups.put(spec.getGroupName(), spec);
        }

        // Merge as needed
        GroupSpec defaultGrpSpec = groups.containsKey("default") ?
            groups.remove("default") :
            GroupSpec.includesOnly("default", "Tag.*");
        Optional<Set<String>> defaultIncludes = Optional.ofNullable(defaultGrpSpec.getIncludes());
        Optional<Set<String>> defaultExcludes = Optional.ofNullable(defaultGrpSpec.getExcludes());

        // merge default and then compile the specifications
        groups.entrySet().stream()
            .map(e -> {
                GroupSpec grp = e.getValue();
                grp.setGroupName(e.getKey());
                grp.setIncludes(
                    Optional.ofNullable(grp.getIncludes())
                        .map(s ->
                            defaultIncludes.
                                map(ds -> { s.addAll(ds); return s; })
                                .orElse(s)
                        )
                        .orElseGet(defaultGrpSpec::getIncludes));
                grp.setExcludes(
                    Optional.ofNullable(grp.getExcludes())
                        .map(s ->
                            defaultExcludes
                                .map(ds -> { s.addAll(ds); return s; })
                                .orElse(s)
                        )
                        .orElseGet(defaultGrpSpec::getExcludes)
                );
                return e;
            })
            .map(Map.Entry::getValue)
            .forEach(GroupSpec::compile);
    }
}
