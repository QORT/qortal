package org.qortal.api;

import org.eclipse.persistence.oxm.annotations.XmlVariableNode;
import org.qortal.transaction.Transaction.TransactionType;

import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class TransactionCountMapXmlAdapter extends XmlAdapter<TransactionCountMapXmlAdapter.StringIntegerMap, Map<TransactionType, Integer>> {

	public static class StringIntegerMap {
		@XmlVariableNode("key")
		List<MapEntry> entries = new ArrayList<>();
	}

	public static class MapEntry {
		@XmlTransient
		public String key;

		@XmlValue
		public Integer value;
	}

	@Override
	public Map<TransactionType, Integer> unmarshal(StringIntegerMap stringIntegerMap) throws Exception {
		Map<TransactionType, Integer> map = new EnumMap<>(TransactionType.class);

		for (MapEntry entry : stringIntegerMap.entries)
			map.put(TransactionType.valueOf(entry.key), entry.value);

		return map;
	}

	@Override
	public StringIntegerMap marshal(Map<TransactionType, Integer> map) throws Exception {
		StringIntegerMap output = new StringIntegerMap();

		for (Entry<TransactionType, Integer> entry : map.entrySet()) {
			MapEntry mapEntry = new MapEntry();
			mapEntry.key = entry.getKey().name();
			mapEntry.value = entry.getValue();
			output.entries.add(mapEntry);
		}

		return output;
	}
}
