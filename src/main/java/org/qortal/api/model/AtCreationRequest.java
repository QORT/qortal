package org.qortal.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.DecoderException;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

@XmlAccessorType(XmlAccessType.FIELD)
public class AtCreationRequest {

    @Schema(description = "CIYAM AT version", example = "2")
    private short ciyamAtVersion;

    @Schema(description = "base64-encoded code bytes", example = "")
    private String codeBytesBase64;

    @Schema(description = "base64-encoded data bytes", example = "")
    private String dataBytesBase64;

    private short numCallStackPages;
    private short numUserStackPages;
    private long minActivationAmount;

    // Default constructor for JSON deserialization
    public AtCreationRequest() {}

    // Getters and setters
    public short getCiyamAtVersion() {
        return ciyamAtVersion;
    }

    public void setCiyamAtVersion(short ciyamAtVersion) {
        this.ciyamAtVersion = ciyamAtVersion;
    }


    public String getCodeBytesBase64() {
        return this.codeBytesBase64;
    }

    @XmlTransient
    @Schema(hidden = true)
    public byte[] getCodeBytes() {
        if (this.codeBytesBase64 != null) {
            try {
                return Base64.decode(this.codeBytesBase64);
            }
            catch (DecoderException e) {
                return null;
            }
        }
        return null;
    }


    public String getDataBytesBase64() {
        return this.dataBytesBase64;
    }

    @XmlTransient
    @Schema(hidden = true)
    public byte[] getDataBytes() {
        if (this.dataBytesBase64 != null) {
            try {
                return Base64.decode(this.dataBytesBase64);
            }
            catch (DecoderException e) {
                return null;
            }
        }
        return null;
    }


    public short getNumCallStackPages() {
        return numCallStackPages;
    }

    public void setNumCallStackPages(short numCallStackPages) {
        this.numCallStackPages = numCallStackPages;
    }

    public short getNumUserStackPages() {
        return numUserStackPages;
    }

    public void setNumUserStackPages(short numUserStackPages) {
        this.numUserStackPages = numUserStackPages;
    }

    public long getMinActivationAmount() {
        return minActivationAmount;
    }

    public void setMinActivationAmount(long minActivationAmount) {
        this.minActivationAmount = minActivationAmount;
    }
}
