package aws.cfn.codegen.json;

public enum SchemaDraft {
    draft04("http://json-schema.org/draft-04/schema#"),
    draft07("http://json-schema.org/draft-07/schema#");

    public String getLocation() {
        return location;
    }

    private final String location;
    SchemaDraft(String location) {
        this.location = location;
    }
}
