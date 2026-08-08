package org.qortal.data.arbitrary;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class ArbitraryResourceNameInfo {

	public String name;
	public List<ArbitraryResourceData> resources = new ArrayList<>();

	public ArbitraryResourceNameInfo() {
	}

}
