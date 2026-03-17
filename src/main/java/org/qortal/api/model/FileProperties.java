package org.qortal.api.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class FileProperties {

	public String filename;
	public String mimeType;
	public Long size;

	public FileProperties() {
	}

}
