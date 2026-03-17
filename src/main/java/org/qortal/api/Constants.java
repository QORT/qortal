package org.qortal.api;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;

import java.util.List;

import static java.util.Arrays.asList;

class Constants {
	public static final String APIERROR_CONTEXT_PATH = "/Api";
	public static final String APIERROR_KEY = "ApiError/%s";
	
	public static final String TRANSLATION_EXTENSION_NAME = "translation";
	public static final String TRANSLATION_PATH_EXTENSION_NAME = "path";

	public static final String TRANSLATION_ANNOTATION_DESCRIPTION_KEY = "description.key";
	public static final String TRANSLATION_ANNOTATION_SUMMARY_KEY = "summary.key";
	public static final String TRANSLATION_ANNOTATION_TITLE_KEY = "title.key";
	public static final String TRANSLATION_ANNOTATION_TERMS_OF_SERVICE_KEY = "termsOfService.key";

	public static final String API_ERRORS_EXTENSION_NAME = "apiErrors";
	public static final String API_ERROR_CODE_EXTENSION_NAME = "apiErrorCode";
	
	public static final List<TranslatableProperty<Info>> TRANSLATABLE_INFO_PROPERTIES = asList(
		new TranslatableProperty<Info>() {
			@Override public String keyName() { return TRANSLATION_ANNOTATION_DESCRIPTION_KEY; }
			@Override public void setValue(Info item, String translation) { item.setDescription(translation); }
			@Override public String getValue(Info item) { return item.getDescription(); }
		},
		new TranslatableProperty<Info>() {
			@Override public String keyName() { return TRANSLATION_ANNOTATION_TITLE_KEY; }
			@Override public void setValue(Info item, String translation) { item.setTitle(translation); }
			@Override public String getValue(Info item) { return item.getTitle(); }
		},
		new TranslatableProperty<Info>() {
			@Override public String keyName() { return TRANSLATION_ANNOTATION_TERMS_OF_SERVICE_KEY; }
			@Override public void setValue(Info item, String translation) { item.setTermsOfService(translation); }
			@Override public String getValue(Info item) { return item.getTermsOfService(); }
		}
	);
	
	public static final List<TranslatableProperty<PathItem>> TRANSLATABLE_PATH_ITEM_PROPERTIES = asList(
		new TranslatableProperty<PathItem>() {
			@Override public String keyName() { return TRANSLATION_ANNOTATION_DESCRIPTION_KEY; }
			@Override public void setValue(PathItem item, String translation) { item.setDescription(translation); }
			@Override public String getValue(PathItem item) { return item.getDescription(); }
		},
		new TranslatableProperty<PathItem>() {
			@Override public String keyName() { return TRANSLATION_ANNOTATION_SUMMARY_KEY; }
			@Override public void setValue(PathItem item, String translation) { item.setSummary(translation); }
			@Override public String getValue(PathItem item) { return item.getSummary(); }
		}
	);
	
	public static final List<TranslatableProperty<Operation>> TRANSLATABLE_OPERATION_PROPERTIES = asList(
		new TranslatableProperty<Operation>() {
			@Override public String keyName() { return TRANSLATION_ANNOTATION_DESCRIPTION_KEY; }
			@Override public void setValue(Operation item, String translation) { item.setDescription(translation); }
			@Override public String getValue(Operation item) { return item.getDescription(); }
		},
		new TranslatableProperty<Operation>() {
			@Override public String keyName() { return TRANSLATION_ANNOTATION_SUMMARY_KEY; }
			@Override public void setValue(Operation item, String translation) { item.setSummary(translation); }
			@Override public String getValue(Operation item) { return item.getSummary(); }
		}
	);
	
	public static final List<TranslatableProperty<ApiResponse>> TRANSLATABLE_API_RESPONSE_PROPERTIES = asList(
		new TranslatableProperty<ApiResponse>() {
			@Override public String keyName() { return TRANSLATION_ANNOTATION_DESCRIPTION_KEY; }
			@Override public void setValue(ApiResponse item, String translation) { item.setDescription(translation); }
			@Override public String getValue(ApiResponse item) { return item.getDescription(); }
		}
	);	
}
