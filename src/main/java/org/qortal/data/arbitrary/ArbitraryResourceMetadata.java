package org.qortal.data.arbitrary;

import org.qortal.arbitrary.metadata.ArbitraryDataTransactionMetadata;
import org.qortal.arbitrary.misc.Category;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class ArbitraryResourceMetadata {

    private String title;
    private String description;
    private List<String> tags;
    private Category category;
    private String categoryName;
    private List<String> files;
    private String mimeType;

    // Only included when updating database
    private ArbitraryResourceData arbitraryResourceData;

    public ArbitraryResourceMetadata() {
    }

    public ArbitraryResourceMetadata(String title, String description, List<String> tags, Category category, List<String> files, String mimeType) {
        this.title = title;
        this.description = description;
        this.tags = tags;
        this.category = category;
        this.files = files;
        this.mimeType = mimeType;

        if (category != null) {
            this.categoryName = category.getName();
        }
    }

    public static ArbitraryResourceMetadata fromTransactionMetadata(ArbitraryDataTransactionMetadata transactionMetadata, boolean includeFileList) {
        if (transactionMetadata == null) {
            return null;
        }
        String title = transactionMetadata.getTitle();
        String description = transactionMetadata.getDescription();
        List<String> tags = transactionMetadata.getTags();
        Category category = transactionMetadata.getCategory();
        String mimeType = transactionMetadata.getMimeType();

        // We don't always want to include the file list as it can be too verbose
        List<String> files = null;
        if (includeFileList) {
            files = transactionMetadata.getFiles();
        }

        if (title == null && description == null && tags == null && category == null && files == null && mimeType == null) {
            return null;
        }

        return new ArbitraryResourceMetadata(title, description, tags, category, files, mimeType);
    }

    public List<String> getFiles() {
        return this.files;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return this.title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getTags() {
        return this.tags;
    }

    public void setCategory(Category category) {
        this.category = category;

        // Also set categoryName
        if (category != null) {
            this.categoryName = category.getName();
        }
    }

    public Category getCategory() {
        return this.category;
    }

    public boolean hasMetadata() {
        return title != null || description != null || tags != null || category != null || files != null || mimeType != null;
    }

    public void setArbitraryResourceData(ArbitraryResourceData arbitraryResourceData) {
        this.arbitraryResourceData = arbitraryResourceData;
    }
    public ArbitraryResourceData getArbitraryResourceData() {
        return this.arbitraryResourceData;
    }
}
