import com.google.gson.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Curated public addresses only. Creates new documents; never assigns a demo Host or overwrites rooms. */
public class RentalPlacesDataset {
    static final Path INPUT=Path.of("data/reference/rental-places-hcm.json");
    static JsonObject fields(JsonObject place){
        JsonObject fields=new JsonObject();
        for(Map.Entry<String,JsonElement> entry:place.entrySet()){
            String key=entry.getKey();JsonObject value=new JsonObject();
            if(key.equals("location"))value.add("geoPointValue",entry.getValue());
            else if(key.equals("checkedAt")){Instant.parse(entry.getValue().getAsString());value.addProperty("timestampValue",entry.getValue().getAsString());}
            else value.addProperty("stringValue",entry.getValue().getAsString());
            fields.add(key,value);
        }return fields;
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1||!Arrays.asList("apply","verify").contains(args[0]))throw new IllegalArgumentException("Use apply or verify");
        JsonObject config=JsonParser.parseString(Files.readString(Path.of("app/google-services.json"))).getAsJsonObject();
        if(!FirestoreDataset.PROJECT.equals(config.getAsJsonObject("project_info").get("project_id").getAsString()))throw new IllegalStateException("Wrong project");
        String tokenFile=System.getenv("BROOM_DATASET_TOKEN_FILE");String token;
        if(tokenFile!=null)token=Files.readString(Path.of(tokenFile)).trim();
        else if(args[0].equals("verify")){
            FirestoreDataset.apiKey=config.getAsJsonArray("client").get(0).getAsJsonObject().getAsJsonArray("api_key").get(0).getAsJsonObject().get("current_key").getAsString();
            Properties credentials=new Properties();try(java.io.Reader in=Files.newBufferedReader(Path.of("data/demo/credentials.local.properties"))){credentials.load(in);}
            token=FirestoreDataset.auth("signInWithPassword",credentials.getProperty("admin.email"),credentials.getProperty("admin.password"));
        }else throw new IllegalArgumentException("Provide BROOM_DATASET_TOKEN_FILE outside repository");
        JsonArray places=JsonParser.parseString(Files.readString(INPUT)).getAsJsonArray();
        JsonArray writes=new JsonArray();List<String> verified=new ArrayList<>();
        for(JsonElement item:places){
            JsonObject place=item.getAsJsonObject();String id=place.get("id").getAsString();
            if(!id.matches("ref_hcm_[a-z0-9_]+"))throw new IllegalArgumentException("Invalid reference ID");
            if(!place.get("sourceUrl").getAsString().startsWith("https://"))throw new IllegalArgumentException("Source required");
            JsonObject doc=new JsonObject();doc.addProperty("name",FirestoreDataset.ROOT+"rentalPlaces/"+id);doc.add("fields",fields(place));
            JsonElement existing=FirestoreDataset.request("GET",FirestoreDataset.API+doc.get("name").getAsString(),token,null,200,404);
            if(existing.isJsonObject()&&existing.getAsJsonObject().has("fields")){
                if(!existing.getAsJsonObject().get("fields").equals(doc.get("fields")))throw new IllegalStateException("Existing reference differs: "+id);
            }else if(args[0].equals("apply")){
                JsonObject write=new JsonObject();write.add("update",doc);JsonObject pre=new JsonObject();pre.addProperty("exists",false);write.add("currentDocument",pre);writes.add(write);
            }else throw new IllegalStateException("Missing reference: "+id);
            verified.add(id);
        }
        if(!writes.isEmpty()){JsonObject body=new JsonObject();body.add("writes",writes);FirestoreDataset.request("POST",FirestoreDataset.API+FirestoreDataset.ROOT.substring(0,FirestoreDataset.ROOT.length()-1)+":commit",token,body,200);}
        // Read back every field from cloud even after a successful commit response.
        for(JsonElement item:places){JsonObject p=item.getAsJsonObject();JsonObject actual=FirestoreDataset.request("GET",FirestoreDataset.API+FirestoreDataset.ROOT+"rentalPlaces/"+p.get("id").getAsString(),token,null,200).getAsJsonObject();
            if(!actual.get("fields").equals(fields(p)))throw new IllegalStateException("Read-back mismatch");}
        JsonObject report=new JsonObject();report.addProperty("verifiedAt",Instant.now().toString());report.addProperty("project",FirestoreDataset.PROJECT);report.addProperty("database",FirestoreDataset.DATABASE);report.addProperty("passed",true);report.addProperty("count",verified.size());report.add("ids",new Gson().toJsonTree(verified));
        Files.writeString(Path.of("data/reference/verification.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        System.out.println("Public reference addresses verified on cloud: "+verified.size());
    }
}
