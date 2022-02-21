package org.qortal.arbitrary.metadata;

import org.json.JSONArray;
import org.json.JSONObject;
import org.qortal.repository.DataException;
import org.qortal.utils.Base58;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArbitraryDataTransactionMetadata extends ArbitraryDataMetadata {

    private List<byte[]> chunks;

    public ArbitraryDataTransactionMetadata(Path filePath) {
        super(filePath);

    }

    @Override
    protected void readJson() throws DataException {
        if (this.jsonString == null) {
            throw new DataException("Transaction metadata JSON string is null");
        }

        List<byte[]> chunksList = new ArrayList<>();
        JSONObject cache = new JSONObject(this.jsonString);
        if (cache.has("chunks")) {
            JSONArray chunks = cache.getJSONArray("chunks");
            if (chunks != null) {
                for (int i=0; i<chunks.length(); i++) {
                    String chunk = chunks.getString(i);
                    if (chunk != null) {
                        chunksList.add(Base58.decode(chunk));
                    }
                }
            }
            this.chunks = chunksList;
        }
    }

    @Override
    protected void buildJson() {
        JSONObject outer = new JSONObject();

        JSONArray chunks = new JSONArray();
        if (this.chunks != null) {
            for (byte[] chunk : this.chunks) {
                chunks.put(Base58.encode(chunk));
            }
        }
        outer.put("chunks", chunks);

        this.jsonString = outer.toString(2);
        LOGGER.trace("Transaction metadata: {}", this.jsonString);
    }


    public void setChunks(List<byte[]> chunks) {
        this.chunks = chunks;
    }

    public List<byte[]> getChunks() {
        return this.chunks;
    }

    public boolean containsChunk(byte[] chunk) {
        for (byte[] c : this.chunks) {
            if (Arrays.equals(c, chunk)) {
                return true;
            }
        }
        return false;
    }

}
