package org.qortal.arbitrary.metadata;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.qortal.arbitrary.ArbitraryDataDiff.ModifiedPath;
import org.qortal.repository.DataException;
import org.qortal.utils.Base58;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class ArbitraryDataMetadataPatch extends ArbitraryDataQortalMetadata {

    private static final Logger LOGGER = LogManager.getLogger(ArbitraryDataMetadataPatch.class);

    private List<Path> addedPaths;
    private List<ModifiedPath> modifiedPaths;
    private List<Path> removedPaths;
    private byte[] previousSignature;
    private byte[] previousHash;
    private byte[] currentHash;

    public ArbitraryDataMetadataPatch(Path filePath) {
        super(filePath);

        this.addedPaths = new ArrayList<>();
        this.modifiedPaths = new ArrayList<>();
        this.removedPaths = new ArrayList<>();
    }

    @Override
    protected String fileName() {
        return "patch";
    }

    @Override
    protected void readJson() throws DataException, JSONException {
        if (this.jsonString == null) {
            throw new DataException("Patch JSON string is null");
        }

        JSONObject patch = new JSONObject(this.jsonString);
        if (patch.has("prevSig")) {
            String prevSig = patch.getString("prevSig");
            if (prevSig != null) {
                this.previousSignature = Base58.decode(prevSig);
            }
        }
        if (patch.has("prevHash")) {
            String prevHash = patch.getString("prevHash");
            if (prevHash != null) {
                this.previousHash = Base58.decode(prevHash);
            }
        }
        if (patch.has("curHash")) {
            String curHash = patch.getString("curHash");
            if (curHash != null) {
                this.currentHash = Base58.decode(curHash);
            }
        }
        if (patch.has("added")) {
            JSONArray added = (JSONArray) patch.get("added");
            if (added != null) {
                for (int i=0; i<added.length(); i++) {
                    String pathString = added.getString(i);
                    this.addedPaths.add(Paths.get(pathString));
                }
            }
        }
        if (patch.has("modified")) {
            JSONArray modified = (JSONArray) patch.get("modified");
            if (modified != null) {
                for (int i=0; i<modified.length(); i++) {
                    JSONObject jsonObject = modified.getJSONObject(i);
                    ModifiedPath modifiedPath = new ModifiedPath(jsonObject);
                    this.modifiedPaths.add(modifiedPath);
                }
            }
        }
        if (patch.has("removed")) {
            JSONArray removed = (JSONArray) patch.get("removed");
            if (removed != null) {
                for (int i=0; i<removed.length(); i++) {
                    String pathString = removed.getString(i);
                    this.removedPaths.add(Paths.get(pathString));
                }
            }
        }
    }

    @Override
    protected void buildJson() {
        JSONObject patch = new JSONObject();
        // Attempt to use a LinkedHashMap so that the order of fields is maintained
        try {
            Field changeMap = patch.getClass().getDeclaredField("map");
            changeMap.setAccessible(true);
            changeMap.set(patch, new LinkedHashMap<>());
            changeMap.setAccessible(false);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            // Don't worry about failures as this is for optional ordering only
        }

        patch.put("prevSig", Base58.encode(this.previousSignature));
        patch.put("prevHash", Base58.encode(this.previousHash));
        patch.put("curHash", Base58.encode(this.currentHash));
        patch.put("added", new JSONArray(this.addedPaths));
        patch.put("removed", new JSONArray(this.removedPaths));

        JSONArray modifiedPaths = new JSONArray();
        for (ModifiedPath modifiedPath : this.modifiedPaths) {
            JSONObject modifiedPathJson = new JSONObject();
            modifiedPathJson.put("path", modifiedPath.getPath());
            modifiedPathJson.put("type", modifiedPath.getDiffType());
            modifiedPaths.put(modifiedPathJson);
        }
        patch.put("modified", modifiedPaths);

        this.jsonString = patch.toString(2);
        LOGGER.debug("Patch metadata: {}", this.jsonString);
    }

    public void setAddedPaths(List<Path> addedPaths) {
        this.addedPaths = addedPaths;
    }

    public List<Path> getAddedPaths() {
        return this.addedPaths;
    }

    public void setModifiedPaths(List<ModifiedPath> modifiedPaths) {
        this.modifiedPaths = modifiedPaths;
    }

    public List<ModifiedPath> getModifiedPaths() {
        return this.modifiedPaths;
    }

    public void setRemovedPaths(List<Path> removedPaths) {
        this.removedPaths = removedPaths;
    }

    public List<Path> getRemovedPaths() {
        return this.removedPaths;
    }

    public void setPreviousSignature(byte[] previousSignature) {
        this.previousSignature = previousSignature;
    }

    public byte[] getPreviousSignature() {
        return this.previousSignature;
    }

    public void setPreviousHash(byte[] previousHash) {
        this.previousHash = previousHash;
    }

    public byte[] getPreviousHash() {
        return this.previousHash;
    }

    public void setCurrentHash(byte[] currentHash) {
        this.currentHash = currentHash;
    }

    public byte[] getCurrentHash() {
        return this.currentHash;
    }


    public int getFileDifferencesCount() {
        return this.addedPaths.size() + this.modifiedPaths.size() + this.removedPaths.size();
    }

}
