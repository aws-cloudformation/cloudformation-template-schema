package aws.cfn.codegen.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Sets;

import java.io.File;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * This represent the configuration that dictate {@link Codegen} to determine specification
 * by regions and generate appropriate template schemas based on the groups that we specified
 * By default if the tool is not provided with a configuration, it loads the default configuration
 * file that is bundled with that package. See config.yml
 *
 * @see GroupSpec
 */
@lombok.Data
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
        private Set<String> regionPrefixes;
        private boolean singleResourceSpec = false;
        private final Map<String, URI> regions = new LinkedHashMap<>(12);
        private final Map<String, GroupSpec> groups = new LinkedHashMap<>(5);

        private Builder(Config existing) {
            mergeOverride(existing);
        }

        public Builder mergeOverride(Config other) {
            Objects.requireNonNull(other);
            Settings settings = other.getSettings();
            if (settings != null) {
                this.draft = settings.getDraft() != null ? settings.getDraft() : this.draft;
                this.outputDir = settings.getOutput() != null ? settings.getOutput() : this.outputDir;
                this.regionPrefixes = settings.getRegions() != null && !settings.getRegions().isEmpty() ?
                    settings.getRegions() : this.regionPrefixes;
                this.singleResourceSpec = settings.getSingle() != null ? settings.getSingle() : this.singleResourceSpec;
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

        public Builder addRegion(String region) {
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
                    regionPrefixes,
                    outputDir,
                    singleResourceSpec
                ),
                groups
            );
        }

    }

    @lombok.Data
    @lombok.ToString
    @lombok.EqualsAndHashCode
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class Settings {
        private SchemaDraft draft;
        private Set<String> regions;
        private File output;
        private Boolean single;

        public Settings mergeOverride(Settings other) {
            if (other == null) return this;
            SchemaDraft draft = other.getDraft();
            Set<String> regions = other.getRegions();
            File output = other.getOutput();
            Boolean single = other.getSingle();
            return new Settings(
                draft == null ? SchemaDraft.draft07 : draft,
                regions == null ? this.regions : regions,
                output == null ? this.output : output,
                single == null ? this.single : single
            );
        }
    }

    private Map<String, GroupSpec> groups;
    private Settings settings;
    private Map<String, URI> specifications;
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

    public void validateRegionSpecs() {
        if (specifications.isEmpty()) {
            specifications.put("us-east-2",
                URI.create("https://dnwj8swjjbsbt.cloudfront.net/latest/gzip/CloudFormationResourceSpecification.json"));
        }
    }

    public void validateGroups() {
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
