/*******************************************************************************
 * Copyright (c) 2013 aegif.
 *
 * This file is part of NemakiWare.
 *
 * NemakiWare is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NemakiWare is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with NemakiWare.
 * If not, see <http://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     linzhixing(https://github.com/linzhixing) - initial API and implementation
 ******************************************************************************/
package jp.aegif.nemaki.cmis.service.impl;

import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.Acl;
import org.apache.chemistry.opencmis.commons.data.AllowableActions;
import org.apache.chemistry.opencmis.commons.data.BulkUpdateObjectIdAndChangeToken;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.data.ExtensionsData;
import org.apache.chemistry.opencmis.commons.data.FailedToDeleteData;
import org.apache.chemistry.opencmis.commons.data.ObjectData;
import org.apache.chemistry.opencmis.commons.data.PermissionMapping;
import org.apache.chemistry.opencmis.commons.data.Properties;
import org.apache.chemistry.opencmis.commons.data.RenditionData;
import org.apache.chemistry.opencmis.commons.definitions.DocumentTypeDefinition;
import org.apache.chemistry.opencmis.commons.definitions.FolderTypeDefinition;
import org.apache.chemistry.opencmis.commons.definitions.RelationshipTypeDefinition;
import org.apache.chemistry.opencmis.commons.definitions.TypeDefinition;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.enums.IncludeRelationships;
import org.apache.chemistry.opencmis.commons.enums.UnfileObject;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.BulkUpdateObjectIdAndChangeTokenImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ContentStreamImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.FailedToDeleteDataImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.RenditionDataImpl;
import org.apache.chemistry.opencmis.commons.server.CallContext;
import org.apache.chemistry.opencmis.commons.spi.Holder;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import jp.aegif.nemaki.businesslogic.ContentService;
import jp.aegif.nemaki.cmis.aspect.CompileService;
import jp.aegif.nemaki.cmis.aspect.ExceptionService;
import jp.aegif.nemaki.cmis.aspect.query.solr.SolrUtil;
import jp.aegif.nemaki.cmis.aspect.type.TypeManager;
import jp.aegif.nemaki.cmis.service.ObjectService;
import jp.aegif.nemaki.model.AttachmentNode;
import jp.aegif.nemaki.model.Content;
import jp.aegif.nemaki.model.Document;
import jp.aegif.nemaki.model.Folder;
import jp.aegif.nemaki.model.Item;
import jp.aegif.nemaki.model.Policy;
import jp.aegif.nemaki.model.Relationship;
import jp.aegif.nemaki.model.Rendition;
import jp.aegif.nemaki.model.VersionSeries;
import jp.aegif.nemaki.util.DataUtil;
import jp.aegif.nemaki.util.cache.NemakiCache;
import jp.aegif.nemaki.util.cache.NemakiCachePool;
import jp.aegif.nemaki.util.constant.DomainType;

public class ObjectServiceImpl implements ObjectService {

	private static final Log log = LogFactory
			.getLog(ObjectServiceImpl.class);
	
	private TypeManager typeManager;
	private ContentService contentService;
	private ExceptionService exceptionService;
	private CompileService compileService;
	private SolrUtil solrUtil;
	private NemakiCachePool nemakiCachePool;

	@Override
	public ObjectData getObjectByPath(CallContext callContext, String repositoryId,
			String path, String filter,
			Boolean includeAllowableActions, IncludeRelationships includeRelationships,
			String renditionFilter, Boolean includePolicyIds,
			Boolean includeAcl, ExtensionsData extension) {
		// //////////////////
		// General Exception
		// //////////////////
		exceptionService.invalidArgumentRequired("objectId", path);
		// FIXME path is not preserved in db.
		Content content = contentService.getContentByPath(repositoryId, path);
		// TODO create objectNotFoundByPath method
		exceptionService.objectNotFound(DomainType.OBJECT, content, path);
		exceptionService.permissionDenied(callContext,
				repositoryId, PermissionMapping.CAN_GET_PROPERTIES_OBJECT, content);

		// //////////////////
		// Body of the method
		// //////////////////
		return compileService.compileObjectData(callContext, repositoryId,
				content, filter, includeAllowableActions,
				includeRelationships, renditionFilter, includeAcl);
	}

	@Override
	public ObjectData getObject(CallContext callContext, String repositoryId,
			String objectId, String filter,
			Boolean includeAllowableActions, IncludeRelationships includeRelationships,
			String renditionFilter, Boolean includePolicyIds,
			Boolean includeAcl, ExtensionsData extension) {
		// //////////////////
		// General Exception
		// //////////////////
		exceptionService.invalidArgumentRequired("objectId", objectId);
		Content content = contentService.getContent(repositoryId, objectId);
		// WORK AROUND: getObject(versionSeriesId) is interpreted as
		// getDocumentOflatestVersion
		if (content == null) {
			VersionSeries versionSeries = contentService
					.getVersionSeries(repositoryId, objectId);
			if (versionSeries != null) {
				content = contentService.getDocumentOfLatestVersion(repositoryId, objectId);
			}
		}
		exceptionService.objectNotFound(DomainType.OBJECT, content, objectId);
		exceptionService.permissionDenied(callContext,
				repositoryId, PermissionMapping.CAN_GET_PROPERTIES_OBJECT, content);

		// //////////////////
		// Body of the method
		// //////////////////
		ObjectData object = compileService.compileObjectData(callContext,
				repositoryId, content, filter, includeAllowableActions,
				includeRelationships, null, includeAcl);

		return object;
	}

	@Override
	public ContentStream getContentStream(CallContext callContext,
			String repositoryId, String objectId, String streamId,
			BigInteger offset, BigInteger length) {
		// //////////////////
		// General Exception
		// //////////////////
		exceptionService.invalidArgumentRequired("objectId", objectId);
		Content content = contentService.getContent(repositoryId, objectId);
		exceptionService.objectNotFound(DomainType.OBJECT, content, objectId);
		exceptionService.permissionDenied(callContext,
				repositoryId, PermissionMapping.CAN_GET_PROPERTIES_OBJECT, content);

		// //////////////////
		// Body of the method
		// //////////////////
		if (streamId == null) {
			return getContentStreamInternal(repositoryId, content, offset, length);
		} else {
			return getRenditionStream(repositoryId, content, streamId);
		}
	}

	// TODO Implement HTTP range(offset and length of stream), though it is not
	// obligatory.
	private ContentStream getContentStreamInternal(String repositoryId,
			Content content, BigInteger rangeOffset, BigInteger rangeLength) {
		if (!content.isDocument()) {
			exceptionService
					.constraint(content.getId(),
							"getContentStream cannnot be invoked to other than document type.");
		}
		Document document = (Document) content;
		exceptionService.constraintContentStreamDownload(repositoryId, document);
		AttachmentNode attachment = contentService.getAttachment(repositoryId, document
				.getAttachmentNodeId());
		attachment.setRangeOffset(rangeOffset);
		attachment.setRangeLength(rangeLength);

		// Set content stream
		BigInteger length = BigInteger.valueOf(attachment.getLength());
		String name = attachment.getName();
		String mimeType = attachment.getMimeType();
		InputStream is = attachment.getInputStream();
		ContentStream cs = new ContentStreamImpl(name, length, mimeType, is);

		return cs;
	}

	private ContentStream getRenditionStream(String repositoryId, Content content, String streamId) {
		if (!content.isDocument() && !content.isFolder()) {
			exceptionService
					.constraint(content.getId(),
							"getRenditionStream cannnot be invoked to other than document or folder type.");
		}
		
		exceptionService.constraintRenditionStreamDownload(content, streamId);
		
		Rendition rendition = contentService.getRendition(repositoryId, streamId);

		BigInteger length = BigInteger.valueOf(rendition.getLength());
		String mimeType = rendition.getMimetype();
		InputStream is = rendition.getInputStream();
		ContentStream cs = new ContentStreamImpl("preview_" + streamId, length, mimeType, is);

		return cs;
	}
	
	@Override
	public List<RenditionData> getRenditions(CallContext callContext,
			String repositoryId, String objectId, String renditionFilter,
			BigInteger maxItems, BigInteger skipCount, ExtensionsData extension) {
		List<Rendition> renditions = contentService.getRenditions(repositoryId, objectId);

		List<RenditionData> results = new ArrayList<RenditionData>();
		for (Rendition rnd : renditions) {
			RenditionDataImpl data = new RenditionDataImpl(rnd.getId(),
					rnd.getMimetype(), BigInteger.valueOf(rnd.getLength()),
					rnd.getKind(), rnd.getTitle(), BigInteger.valueOf(rnd
							.getWidth()), BigInteger.valueOf(rnd.getHeight()),
					rnd.getRenditionDocumentId());
			results.add(data);
		}
		return results;
	}

	@Override
	public AllowableActions getAllowableActions(CallContext callContext,
			String repositoryId, String objectId) {
		// //////////////////
		// General Exception
		// //////////////////
		exceptionService.invalidArgumentRequired("objectId", objectId);
		Content content = contentService.getContent(repositoryId, objectId);
		exceptionService.objectNotFound(DomainType.OBJECT, content, objectId);
		// NOTE: The permission key doesn't exist according to CMIS
		// specification.

		// //////////////////
		// Body of the method
		// //////////////////
		return compileService.compileAllowableActions(callContext,
				repositoryId, content);
	}

	@Override
	public ObjectData create(CallContext callContext, String repositoryId,
			Properties properties, String folderId,
			ContentStream contentStream, VersioningState versioningState,
			List<String> policies, ExtensionsData extension) {

		String typeId = DataUtil.getObjectTypeId(properties);
		TypeDefinition type = typeManager.getTypeDefinition(repositoryId, typeId);
		if (type == null) {
			throw new CmisObjectNotFoundException("Type '" + typeId
					+ "' is unknown!");
		}

		String objectId = null;
		// TODO ACE can be set !
		if (type.getBaseTypeId() == BaseTypeId.CMIS_DOCUMENT) {
			objectId = createDocument(callContext, repositoryId, properties,
					folderId, contentStream, versioningState, null, null, null);
		} else if (type.getBaseTypeId() == BaseTypeId.CMIS_FOLDER) {
			objectId = createFolder(callContext, repositoryId, properties,
					folderId, policies, null, null, extension);
		} else if (type.getBaseTypeId() == BaseTypeId.CMIS_RELATIONSHIP) {
			objectId = createRelationship(callContext, repositoryId, properties,
					policies, null, null, extension);
		} else if (type.getBaseTypeId() == BaseTypeId.CMIS_POLICY) {
			objectId = createPolicy(callContext, repositoryId, properties, folderId,
					policies, null, null, extension);
		} else if (type.getBaseTypeId() == BaseTypeId.CMIS_ITEM) {
			objectId = createItem(callContext, repositoryId, properties, folderId,
					policies, null, null, extension);
		} else {
			throw new CmisObjectNotFoundException(
					"Cannot create object of type '" + typeId + "'!");
		}

		return compileService.compileObjectData(callContext,
				repositoryId, contentService.getContent(repositoryId, objectId), null,
				false, IncludeRelationships.NONE, null, false);
	}

	@Override
	public String createFolder(CallContext callContext, String repositoryId,
			Properties properties, String folderId, List<String> policies,
			Acl addAces, Acl removeAces, ExtensionsData extension) {
		FolderTypeDefinition td = (FolderTypeDefinition) typeManager
				.getTypeDefinition(repositoryId, DataUtil.getObjectTypeId(properties));
		Folder parentFolder = contentService.getFolder(repositoryId, folderId);

		// //////////////////
		// General Exception
		// //////////////////
		exceptionService.objectNotFoundParentFolder(repositoryId, folderId, parentFolder);
		exceptionService.permissionDenied(callContext,
				repositoryId, PermissionMapping.CAN_CREATE_FOLDER_FOLDER, parentFolder);

		// //////////////////
		// Specific Exception
		// //////////////////
		exceptionService.constraintBaseTypeId(repositoryId,
				properties, BaseTypeId.CMIS_FOLDER);
		exceptionService.constraintAllowedChildObjectTypeId(parentFolder,
				properties);
		exceptionService.constraintPropertyValue(repositoryId, td,
				properties, DataUtil.getIdProperty(properties, PropertyIds.OBJECT_ID));
		exceptionService
				.constraintCotrollablePolicies(td, policies, properties);
		exceptionService.constraintCotrollableAcl(td, addAces, removeAces,
				properties);
		exceptionService.constraintPermissionDefined(repositoryId, addAces, null);
		exceptionService.constraintPermissionDefined(repositoryId, removeAces, null);
		exceptionService.nameConstraintViolation(properties, parentFolder);

		// //////////////////
		// Body of the method
		// //////////////////
		Folder folder = contentService.createFolder(callContext, repositoryId,
				properties, parentFolder);
		return folder.getId();
	}

	@Override
	public String createDocument(CallContext callContext,
			String repositoryId, Properties properties,
			String folderId, ContentStream contentStream,
			VersioningState versioningState, List<String> policies, Acl addAces, Acl removeAces) {
		String objectTypeId = DataUtil.getIdProperty(properties,
				PropertyIds.OBJECT_TYPE_ID);
		DocumentTypeDefinition td = (DocumentTypeDefinition) typeManager
				.getTypeDefinition(repositoryId, objectTypeId);
		Folder parentFolder = contentService.getFolder(repositoryId, folderId);

		// //////////////////
		// General Exception
		// //////////////////
		exceptionService.invalidArgumentRequired("properties", properties);
		exceptionService.invalidArgumentRequiredParentFolderId(repositoryId, folderId);
		exceptionService.objectNotFoundParentFolder(repositoryId, folderId, parentFolder);
		exceptionService.permissionDenied(callContext,
				repositoryId, PermissionMapping.CAN_CREATE_FOLDER_FOLDER, parentFolder);

		// //////////////////
		// Specific Exception
		// //////////////////
		exceptionService.constraintBaseTypeId(repositoryId,
				properties, BaseTypeId.CMIS_DOCUMENT);
		exceptionService.constraintAllowedChildObjectTypeId(parentFolder,
				properties);
		exceptionService.constraintPropertyValue(repositoryId, td,
				properties, DataUtil.getIdProperty(properties, PropertyIds.OBJECT_ID));
		exceptionService.constraintContentStreamRequired(td, contentStream);
		exceptionService.constraintControllableVersionable(td, versioningState,
				null);
		versioningState = (td.isVersionable() && versioningState == null) ? VersioningState.MAJOR : versioningState;
		exceptionService
				.constraintCotrollablePolicies(td, policies, properties);
		exceptionService.constraintCotrollableAcl(td, addAces, removeAces,
				properties);
		exceptionService.constraintPermissionDefined(repositoryId, addAces, null);
		exceptionService.constraintPermissionDefined(repositoryId, removeAces, null);
		exceptionService.streamNotSupported(td, contentStream);
		exceptionService.nameConstraintViolation(properties, parentFolder);

		// //////////////////
		// Body of the method
		// //////////////////
		Document document = contentService.createDocument(callContext,
				repositoryId, properties, parentFolder, contentStream, versioningState, null);
		return document.getId();
	}

	@Override
	public String createDocumentFromSource(CallContext callContext,
			String repositoryId, String sourceId, Properties properties,
			String folderId, VersioningState versioningState,
			List<String> policies, Acl addAces, Acl removeAces) {
		Document original = contentService.getDocument(repositoryId, sourceId);
		DocumentTypeDefinition td = (DocumentTypeDefinition) typeManager
				.getTypeDefinition(repositoryId, original.getObjectType());

		// //////////////////
		// General Exception
		// //////////////////
		exceptionService.invalidArgumentRequired("properties", properties);
		exceptionService.invalidArgumentRequiredParentFolderId(repositoryId, folderId);
		Folder parentFolder = contentService.getFolder(repositoryId, folderId);
		exceptionService.objectNotFoundParentFolder(repositoryId, folderId, parentFolder);
		exceptionService.permissionDenied(callContext,
				repositoryId, PermissionMapping.CAN_CREATE_FOLDER_FOLDER, parentFolder);

		// //////////////////
		// Specific Exception
		// //////////////////
		exceptionService.constraintBaseTypeId(repositoryId,
				properties, BaseTypeId.CMIS_DOCUMENT);
		exceptionService.constraintAllowedChildObjectTypeId(parentFolder,
				properties);
		exceptionService.constraintPropertyValue(repositoryId, td,
				properties, DataUtil.getIdProperty(properties, PropertyIds.OBJECT_ID));
		exceptionService.constraintControllableVersionable(td, versioningState,
				null);
		versioningState = (td.isVersionable() && versioningState == null) ? VersioningState.MAJOR : versioningState;
		exceptionService
				.constraintCotrollablePolicies(td, policies, properties);
		exceptionService.constraintCotrollableAcl(td, addAces, removeAces,
				properties);
		exceptionService.constraintPermissionDefined(repositoryId, addAces, null);
		exceptionService.constraintPermissionDefined(repositoryId, removeAces, null);
		exceptionService.nameConstraintViolation(properties, parentFolder);

		// //////////////////
		// Body of the method
		// //////////////////
		Document document = contentService.createDocumentFromSource(
				callContext, repositoryId, properties, parentFolder,
				original, versioningState, policies, addAces, removeAces);
		return document.getId();
	}

	@Override
	public void setContentStream(CallContext callContext,
			String repositoryId, Holder<String> objectId,
			boolean overwriteFlag, ContentStream contentStream, Holder<String> changeToken) {
		// //////////////////
		// General Exception
		// //////////////////
		String id = objectId.getValue();

		exceptionService.invalidArgumentRequiredString("objectId", id);
		exceptionService
				.invalidArgumentRequired("contentStream", contentStream);
		Document doc = (Document) contentService.getContent(repositoryId, id);
		exceptionService.objectNotFound(DomainType.OBJECT, doc, id);
		exceptionService.permissionDenied(callContext,
				repositoryId, PermissionMapping.CAN_SET_CONTENT_DOCUMENT, doc);
		DocumentTypeDefinition td = (DocumentTypeDefinition) typeManager
				.getTypeDefinition(repositoryId, doc.getObjectType());
		exceptionService.constraintImmutable(repositoryId, doc, td);

		// //////////////////
		// Specific Exception
		// //////////////////
		exceptionService.contentAlreadyExists(doc, overwriteFlag);
		exceptionService.streamNotSupported(td, contentStream);
		exceptionService.updateConflict(doc, changeToken);
		exceptionService.versioning(doc);
		Folder parent = contentService.getParent(repositoryId, id);
		exceptionService.objectNotFoundParentFolder(repositoryId, id, parent);

		// //////////////////
		// Body of the method
		// //////////////////
		String oldId = objectId.getValue(); 
		
		// TODO Externalize versioningState
		if(doc.isPrivateWorkingCopy()){
			Document result = contentService.replacePwc(callContext, repositoryId,
					doc, contentStream);
			objectId.setValue(result.getId());
		}else{
			Document result = contentService.createDocumentWithNewStream(callContext, repositoryId,
					doc, contentStream);
			objectId.setValue(result.getId());
		}
		
		nemakiCachePool.get(repositoryId).removeCmisCache(oldId);
	}

	@Override
	public void deleteContentStream(CallContext callContext,
			String repositoryId, Holder<String> objectId,
			Holder<String> changeToken, ExtensionsData extension) {
		// //////////////////
		// Exception
		// //////////////////
		exceptionService.invalidArgumentRequiredHolderString("objectId",
				objectId);
		Document document = contentService.getDocument(repositoryId, objectId.getValue());
		exceptionService.objectNotFound(DomainType.OBJECT, document,
				document.getId());
		exceptionService.constraintContentStreamRequired(repositoryId, document);
		
		// //////////////////
		// Body of the method
		// //////////////////
		contentService.deleteContentStream(callContext, repositoryId, objectId);
		
		nemakiCachePool.get(repositoryId).removeCmisCache(objectId.getValue());
	}

	@Override
	public void appendContentStream(CallContext callContext,
			String repositoryId, Holder<String> objectId,
			Holder<String> changeToken, ContentStream contentStream,
			boolean isLastChunk, ExtensionsData extension) {
		// //////////////////
		// General Exception
		// //////////////////
		String id = objectId.getValue();

		exceptionService.invalidArgumentRequiredString("objectId", id);
		exceptionService
				.invalidArgumentRequired("contentStream", contentStream);
		Document doc = (Document) contentService.getContent(repositoryId, id);
		exceptionService.objectNotFound(DomainType.OBJECT, doc, id);
		exceptionService.permissionDenied(callContext,
				repositoryId, PermissionMapping.CAN_SET_CONTENT_DOCUMENT, doc);
		DocumentTypeDefinition td = (DocumentTypeDefinition) typeManager
				.getTypeDefinition(repositoryId, doc.getObjectType());
		exceptionService.constraintImmutable(repositoryId, doc, td);

		// //////////////////
		// Specific Exception
		// //////////////////
		exceptionService.streamNotSupported(td, contentStream);
		exceptionService.updateConflict(doc, changeToken);
		exceptionService.versioning(doc);

		// //////////////////
		// Body of the method
		// //////////////////
		contentService.appendAttachment(callContext, repositoryId, objectId,
				changeToken, contentStream, isLastChunk, extension);
		
		nemakiCachePool.get(repositoryId).removeCmisCache(objectId.getValue());
	}

	@Override
	public String createRelationship(CallContext callContext,
			String repositoryId, Properties properties, List<String> policies,
			Acl addAces, Acl removeAces, ExtensionsData extension) {
		String objectTypeId = DataUtil.getIdProperty(properties,
				PropertyIds.OBJECT_TYPE_ID);
		RelationshipTypeDefinition td = (RelationshipTypeDefinition) typeManager
				.getTypeDefinition(repositoryId, objectTypeId);
		// //////////////////
		// Exception
		// //////////////////
		exceptionService.invalidArgumentRequiredCollection("properties",
				properties.getPropertyList());
		String sourceId = DataUtil.getIdProperty(properties,
				PropertyIds.SOURCE_ID);
		if (sourceId != null) {
			Content source = contentService.getContent(repositoryId, DataUtil
					.getStringProperty(properties, PropertyIds.SOURCE_ID));
			if (source == null)
				exceptionService.constraintAllowedSourceTypes(td, source);
			exceptionService.permissionDenied(callContext,
					repositoryId, PermissionMapping.CAN_CREATE_RELATIONSHIP_SOURCE, source);
		}
		String targetId = DataUtil.getIdProperty(properties,
				PropertyIds.TARGET_ID);
		if (targetId != null) {
			Content target = contentService.getContent(repositoryId, DataUtil
					.getStringProperty(properties, PropertyIds.TARGET_ID));
			if (target == null)
				exceptionService.constraintAllowedTargetTypes(td, target);
			exceptionService.permissionDenied(callContext,
					repositoryId, PermissionMapping.CAN_CREATE_RELATIONSHIP_TARGET, target);
		}

		exceptionService.constraintBaseTypeId(repositoryId,
				properties, BaseTypeId.CMIS_RELATIONSHIP);
		exceptionService.constraintPropertyValue(repositoryId, td,
				properties, DataUtil.getIdProperty(properties, PropertyIds.OBJECT_ID));
		exceptionService
				.constraintCotrollablePolicies(td, policies, properties);
		exceptionService.constraintCotrollableAcl(td, addAces, removeAces,
				properties);
		exceptionService.constraintPermissionDefined(repositoryId, addAces, null);
		exceptionService.constraintPermissionDefined(repositoryId, removeAces, null);
		exceptionService.nameConstraintViolation(properties, null);

		// //////////////////
		// Body of the method
		// //////////////////
		Relationship relationship = contentService.createRelationship(
				callContext, repositoryId, properties, policies, addAces,
				removeAces, extension);
		nemakiCachePool.get(repositoryId).removeCmisCache(relationship.getSourceId());
		nemakiCachePool.get(repositoryId).removeCmisCache(relationship.getTargetId());
		
		return relationship.getId();
	}

	@Override
	public String createPolicy(CallContext callContext, String repositoryId,
			Properties properties, String folderId, List<String> policies,
			Acl addAces, Acl removeAces, ExtensionsData extension) {
		// //////////////////
		// General Exception
		// //////////////////
		exceptionService.invalidArgumentRequiredCollection("properties",
				properties.getPropertyList());
		// NOTE: folderId is ignored because policy is not filable in Nemaki
		TypeDefinition td = typeManager.getTypeDefinition(repositoryId, DataUtil
				.getIdProperty(properties, PropertyIds.OBJECT_TYPE_ID));
		exceptionService.constraintPropertyValue(repositoryId, td,
				properties, DataUtil.getIdProperty(properties, PropertyIds.OBJECT_ID));

		// //////////////////
		// Specific Exception
		// //////////////////
		exceptionService.constraintBaseTypeId(repositoryId,
				properties, BaseTypeId.CMIS_POLICY);
		// exceptionService.constraintAllowedChildObjectTypeId(parent,
		// properties);
		exceptionService
				.constraintCotrollablePolicies(td, policies, properties);
		exceptionService.constraintCotrollableAcl(td, addAces, removeAces,
				properties);
		// exceptionService.nameConstraintViolation(properties, parent);

		// //////////////////
		// Body of the method
		// //////////////////
		Policy policy = contentService.createPolicy(callContext, repositoryId,
				properties, policies, addAces, removeAces, extension);
		return policy.getId();
	}

	@Override
	public String createItem(CallContext callContext, String repositoryId,
			Properties properties, String folderId, List<String> policies,
			Acl addAces, Acl removeAces, ExtensionsData extension) {
		// //////////////////
		// General Exception
		// //////////////////
		TypeDefinition td = typeManager.getTypeDefinition(repositoryId, DataUtil
				.getObjectTypeId(properties));
		Folder parentFolder = contentService.getFolder(repositoryId, folderId);
		exceptionService.objectNotFoundParentFolder(repositoryId, folderId, parentFolder);
		exceptionService.invalidArgumentRequiredCollection("properties",
				properties.getPropertyList());

		// //////////////////
		// Specific Exception
		// //////////////////
		exceptionService.constraintBaseTypeId(repositoryId, properties, BaseTypeId.CMIS_ITEM);
		exceptionService.constraintPropertyValue(repositoryId, td,
				properties, DataUtil.getIdProperty(properties, PropertyIds.OBJECT_ID));
		exceptionService
				.constraintCotrollablePolicies(td, policies, properties);
		exceptionService.constraintCotrollableAcl(td, addAces, removeAces,
				properties);

		// //////////////////
		// Body of the method
		// //////////////////
		Item item = contentService.createItem(callContext, repositoryId,
				properties, folderId, policies, addAces, removeAces, extension);
		return item.getId();
	}

	@Override
	public void updateProperties(CallContext callContext,
			String repositoryId, Holder<String> objectId,
			Properties properties, Holder<String> changeToken) {

		// //////////////////
		// Exception
		// //////////////////
		Content content = checkExceptionBeforeUpdateProperties(callContext,
				repositoryId, objectId, properties, changeToken);

		// //////////////////
		// Body of the method
		// //////////////////
		String id = objectId.getValue();
		
		contentService.updateProperties(callContext, repositoryId, properties, content);
		
		nemakiCachePool.get(repositoryId).removeCmisCache(id);
	}

	private Content checkExceptionBeforeUpdateProperties(
			CallContext callContext, String repositoryId,
			Holder<String> objectId, Properties properties, Holder<String> changeToken) {
		// //////////////////
		// General Exception
		// //////////////////
		exceptionService.invalidArgumentRequiredHolderString("objectId",
				objectId);
		exceptionService.invalidArgumentRequiredCollection("properties",
				properties.getPropertyList());
		Content content = contentService.getContent(repositoryId, objectId.getValue());
		exceptionService.objectNotFound(DomainType.OBJECT, content,
				objectId.getValue());
		if (content.isDocument()) {
			Document d = (Document) content;
			exceptionService.versioning(d);
			exceptionService.constraintUpdateWhenCheckedOut(repositoryId, callContext.getUsername(), d);
			TypeDefinition typeDef = typeManager.getTypeDefinition(repositoryId, d);
			exceptionService.constraintImmutable(repositoryId, d, typeDef);
		}
		exceptionService.permissionDenied(callContext,
				repositoryId, PermissionMapping.CAN_UPDATE_PROPERTIES_OBJECT, content);
		exceptionService.updateConflict(content, changeToken);
		
		

		TypeDefinition tdf = typeManager.getTypeDefinition(repositoryId, content);
		exceptionService.constraintPropertyValue(repositoryId, tdf,
				properties, objectId.getValue());

		return content;
	}

	@Override
	public List<BulkUpdateObjectIdAndChangeToken> bulkUpdateProperties(
			CallContext callContext,
			String repositoryId,
			List<BulkUpdateObjectIdAndChangeToken> objectIdAndChangeToken, Properties properties,
			List<String> addSecondaryTypeIds, List<String> removeSecondaryTypeIds, ExtensionsData extension) {
		// //////////////////
		// General Exception
		// //////////////////
		// Each permission is checked at each execution
		exceptionService.invalidArgumentRequiredCollection(
				"objectIdAndChangeToken", objectIdAndChangeToken);
		exceptionService.invalidArgumentSecondaryTypeIds(repositoryId, properties);

		// //////////////////
		// Body of the method
		// //////////////////
		List<BulkUpdateObjectIdAndChangeToken> results = new ArrayList<BulkUpdateObjectIdAndChangeToken>();

		for (BulkUpdateObjectIdAndChangeToken idAndToken : objectIdAndChangeToken) {
			try {
				Content content = checkExceptionBeforeUpdateProperties(
						callContext, repositoryId,
						new Holder<String>(idAndToken.getId()),
						properties, new Holder<String>(idAndToken.getChangeToken()));
				contentService.updateProperties(callContext, repositoryId,
						properties, content);
				nemakiCachePool.get(repositoryId).removeCmisCache(content.getId());

				BulkUpdateObjectIdAndChangeToken result = new BulkUpdateObjectIdAndChangeTokenImpl(
						idAndToken.getId(), content.getId(),
						String.valueOf(content.getChangeToken()));
				results.add(result);
			} catch (Exception e) {
				// Don't throw an error
				// Don't return any BulkUpdateObjectIdAndChangetoken
			}
		}

		return results;
	}

	@Override
	public void moveObject(CallContext callContext, String repositoryId,
			Holder<String> objectId, String sourceFolderId, String targetFolderId) {
		// //////////////////
		// General Exception
		// //////////////////
		exceptionService.invalidArgumentRequiredHolderString("objectId",
				objectId);
		exceptionService.invalidArgumentRequiredString("sourceFolderId",
				sourceFolderId);
		exceptionService.invalidArgumentRequiredString("targetFolderId",
				targetFolderId);
		Content content = contentService.getContent(repositoryId, objectId.getValue());
		exceptionService.objectNotFound(DomainType.OBJECT, content,
				objectId.getValue());
		Folder source = contentService.getFolder(repositoryId, sourceFolderId);
		exceptionService.objectNotFound(DomainType.OBJECT, source,
				sourceFolderId);
		Folder target = contentService.getFolder(repositoryId, targetFolderId);
		exceptionService.objectNotFound(DomainType.OBJECT, target,
				targetFolderId);
		exceptionService.permissionDenied(callContext,
				repositoryId, PermissionMapping.CAN_MOVE_OBJECT, content);
		exceptionService.permissionDenied(callContext,
				repositoryId, PermissionMapping.CAN_MOVE_SOURCE, source);
		exceptionService.permissionDenied(callContext,
				repositoryId, PermissionMapping.CAN_MOVE_TARGET, target);

		// //////////////////
		// Body of the method
		// //////////////////
		contentService.move(repositoryId, content, target);
		
		nemakiCachePool.get(repositoryId).removeCmisCache(content.getId());
	}

	private void deleteObjectInternal(CallContext callContext, String repositoryId,
			String objectId, Boolean allVersions, Boolean deleteWithParent) {
		// //////////////////
		// General Exception
		// //////////////////
		exceptionService.invalidArgumentRequiredString("objectId", objectId);
		Content content = contentService.getContent(repositoryId, objectId);
		exceptionService.objectNotFound(DomainType.OBJECT, content, objectId);
		exceptionService.permissionDenied(callContext,
				repositoryId, PermissionMapping.CAN_DELETE_OBJECT, content);
		exceptionService.constraintDeleteRootFolder(repositoryId, objectId);

		// //////////////////
		// Body of the method
		// //////////////////
		if (content.isDocument()) {
			contentService.deleteDocument(callContext, repositoryId,
					content.getId(), allVersions, deleteWithParent);
		} else if (content.isFolder()) {
			List<Content> children = contentService.getChildren(repositoryId, objectId);
			if (!CollectionUtils.isEmpty(children)) {
				exceptionService
						.constraint(objectId,
								"deleteObject method is invoked on a folder containing objects.");
			}
			contentService.delete(callContext, repositoryId, objectId, deleteWithParent);
			
		} else {
			contentService.delete(callContext, repositoryId, objectId, deleteWithParent);
		}

		nemakiCachePool.get(repositoryId).removeCmisCache(content.getId());
	}
	
	@Override
	public void deleteObject(CallContext callContext, String repositoryId,
			String objectId, Boolean allVersions) {
		deleteObjectInternal(callContext, repositoryId, objectId, allVersions, false);
	}

	@Override
	public FailedToDeleteData deleteTree(CallContext callContext,
			String repositoryId, String folderId, Boolean allVersions,
			UnfileObject unfileObjects, Boolean continueOnFailure, ExtensionsData extension) {
		// //////////////////
		// General Exception
		// //////////////////
		exceptionService.invalidArgumentRequiredString("objectId", folderId);
		Folder folder = contentService.getFolder(repositoryId, folderId);
		exceptionService.permissionDenied(callContext,
				repositoryId, PermissionMapping.CAN_DELETE_TREE_FOLDER, folder);
		exceptionService.constraintDeleteRootFolder(repositoryId, folderId);

		// //////////////////
		// Specific Exception
		// //////////////////
		if (folder == null)
			exceptionService.constraint(folderId,
					"deleteTree cannot be invoked on a non-folder object");

		// //////////////////
		// Body of the method
		// //////////////////
		// Delete descendants
		List<String> failureIds = new ArrayList<String>();
	
		List<Content> children = contentService.getChildren(repositoryId, folderId);
		if (!CollectionUtils.isEmpty(children)) {
			for (Content child : children) {
				try {
					if (child.isFolder()) {
						deleteTree(callContext, repositoryId, child.getId(), allVersions,
								unfileObjects, continueOnFailure, extension);
					} else {
						deleteObjectInternal(callContext, repositoryId, child.getId(), allVersions, true);
					}
				} catch (Exception e) {
					StringBuilder sb = new StringBuilder();
					sb.append("objectId:").append(child.getId()).append(" failed to be deleted.");
					
					if (continueOnFailure) {
						failureIds.add(child.getId());
						log.warn(sb.toString(), e);
						continue;
					} else {
						log.error(sb.toString(), e);
					}
				}
			}
		}

		// Delete the folder itself
		try {
			deleteObjectInternal(callContext, repositoryId, folderId, allVersions, false);
		} catch (Exception e) {
			StringBuilder sb = new StringBuilder();
			sb.append("objectId:").append(folderId).append(" failed to be deleted.");
			
			if (continueOnFailure) {
				failureIds.add(folderId);
				log.warn(sb.toString(), e);
			} else {
				log.error(sb.toString(), e);
			}
		}
		
		/*failureIds = contentService.deleteTree(callContext, folderId, allVersions,
				continueOnFailure, false);*/
		solrUtil.callSolrIndexing(repositoryId);

		// Check FailedToDeleteData
		// FIXME Consider orphans that was failed to be deleted
		FailedToDeleteDataImpl fdd = new FailedToDeleteDataImpl();
		
		if (CollectionUtils.isNotEmpty(failureIds)) {
			fdd.setIds(failureIds);
		} else {
			fdd.setIds(new ArrayList<String>());
		}
		return fdd;
	}

	public void setContentService(ContentService contentService) {
		this.contentService = contentService;
	}

	public void setExceptionService(ExceptionService exceptionService) {
		this.exceptionService = exceptionService;
	}

	public void setCompileService(CompileService compileService) {
		this.compileService = compileService;
	}

	public void setTypeManager(TypeManager typeManager) {
		this.typeManager = typeManager;
	}

	public void setSolrUtil(SolrUtil solrUtil) {
		this.solrUtil = solrUtil;
	}

	public void setNemakiCachePool(NemakiCachePool nemakiCachePool) {
		this.nemakiCachePool = nemakiCachePool;
	}
}
