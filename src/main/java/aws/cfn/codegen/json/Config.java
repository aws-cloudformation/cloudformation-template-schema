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
        private Set<String> regions;
        private boolean singleResourceSpec = false;
        private boolean includeIntrinsics = true;
        private final Map<String, URI> regionSpecs = new LinkedHashMap<>(12);
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
                this.draft = settings.getDraft() != null ? settings.getDraft() : this.draft;
                this.outputDir = settings.getOutput() != null ? settings.getOutput() : this.outputDir;
                this.regions = settings.getRegions() != null ? settings.getRegions() : this.regions;
                this.singleResourceSpec = settings.getSingle() != null ? settings.getSingle() : this.singleResourceSpec;
                this.includeIntrinsics = settings.getIncludeIntrinsics() != null ? settings.getIncludeIntrinsics() :
                    this.includeIntrinsics;
            }
            this.regionSpecs.putAll(other.getSpecifications());
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
            regionSpecs.put(
                Objects.requireNonNull(region),
                location);
            return this;
        }

        public Builder addRegion(String region) {
            this.regions.add(Objects.requireNonNull(region));
            return this;
        }

        public Builder addRegions(Set<String> regions) {
            this.regions.addAll(regions);
            return this;
        }

        public Builder setRegions(Set<String> regions) {
            this.regions = Objects.requireNonNull(regions);
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

        public Builder withIntrinsics(boolean includeIntrinsics) {
            this.includeIntrinsics = includeIntrinsics;
            return this;
        }

        public Builder withGroup(String grpName, GroupSpec spec) {
            groups.put(grpName, spec);
            return this;
        }

        public Config build() {
            return new Config(
                regionSpecs,
                new Settings(
                    draft,
                    regions,
                    outputDir,
                    singleResourceSpec,
                    includeIntrinsics
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
        private final Set<String> regions;
        private final File output;
        private final Boolean single;
        private final Boolean includeIntrinsics;

        @JsonCreator
        public Settings(@JsonProperty("draft") SchemaDraft draft,
                        @JsonProperty("regions") Set<String> regions,
                        @JsonProperty("output") File output,
                        @JsonProperty("single") Boolean single,
                        @JsonProperty("intrinsics") Boolean includeIntrinsics) {
            this.draft = draft;
            this.regions = regions == null ? Sets.newHashSet("us-east-1") : regions;
            this.output = output;
            this.single = single == null ? false : single;
            this.includeIntrinsics = includeIntrinsics == null ? false : includeIntrinsics;
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
        if (settings != null && !specifications.isEmpty()) {
            for (String r: settings.getRegions()) {
                if (!specifications.containsKey(r)) {
                    throw new IllegalArgumentException("No regions mapping was found " + settings.getRegions());
                }
            }
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
