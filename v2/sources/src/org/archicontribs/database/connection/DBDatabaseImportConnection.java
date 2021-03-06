/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */

package org.archicontribs.database.connection;

import java.io.ByteArrayInputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.archicontribs.database.DBDatabaseEntry;
import org.archicontribs.database.DBLogger;
import org.archicontribs.database.DBPlugin;
import org.archicontribs.database.data.DBDatabase;
import org.archicontribs.database.model.DBArchimateModel;
import org.archicontribs.database.model.DBArchimateFactory;
import org.archicontribs.database.model.DBCanvasFactory;
import org.archicontribs.database.model.DBMetadata;
import org.archicontribs.database.model.IDBMetadata;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Display;

import com.archimatetool.editor.diagram.ArchimateDiagramModelFactory;
import com.archimatetool.editor.model.IArchiveManager;
import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IConnectable;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateComponent;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelBendpoint;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IIdentifier;
import com.archimatetool.model.IProperties;
import com.archimatetool.model.IProperty;
import lombok.Getter;

/**
 * This class holds the information required to connect to, to import from and export to a database
 * 
 * @author Herve Jouin
 */
public class DBDatabaseImportConnection extends DBDatabaseConnection {
	private static final DBLogger logger = new DBLogger(DBDatabaseImportConnection.class);

	private boolean isExportConnectionDuplicate = false;

	/**
	 * Opens a connection to a JDBC database using all the connection details
	 */
	public DBDatabaseImportConnection(DBDatabaseEntry databaseEntry) throws ClassNotFoundException, SQLException {
		super(databaseEntry);
	}

	/**
	 * duplicates a connection to a JDBC database to allow switching between importConnection and exportConnection
	 */
	public DBDatabaseImportConnection(DBDatabaseConnection databaseConnection) {
		super();
		assert(databaseConnection != null);
		super.databaseEntry = databaseConnection.databaseEntry;
		super.schema = databaseConnection.schema;
		super.connection = databaseConnection.connection;
		this.isExportConnectionDuplicate = true;
	}

	/**
	 * ResultSet of the current transaction (used by import process to allow the loop to be managed outside the DBdatabase class)
	 */
	private ResultSet currentResultSet = null;

	/**
	 * Gets a component from the database and convert the result into a HashMap<br>
	 * Mainly used in DBGui to compare a component to its database version.
	 * @param component: component to get
	 * @param version: version of the component to get (0 to get the latest version) 
	 * @return HashMap containing the object data
	 * @throws Exception
	 */
	public HashMap<String, Object> getObjectFromDatabase(EObject component, int version) throws Exception {
		String id = ((IIdentifier)component).getId();
		String clazz;
		if ( component instanceof IArchimateElement ) clazz = "IArchimateElement";
		else if ( component instanceof IArchimateRelationship ) clazz = "IArchimateRelationship";
		else if ( component instanceof IFolder ) clazz = "IFolder";
		else if ( component instanceof IDiagramModel ) clazz = "IDiagramModel";
		else if ( component instanceof IDiagramModelArchimateObject	 ) clazz = "IDiagramModelArchimateObject";
		else if ( component instanceof IDiagramModelArchimateConnection	 ) clazz = "IDiagramModelArchimateConnection";
		else throw new Exception("Do not know how to get a "+component.getClass().getSimpleName()+" from the database.");

		return getObject(id, clazz, version);
	}

	/**
	 * Gets a component from the database and convert the result into a HashMap<br>
	 * Mainly used in DBGui to compare a component to its database version.
	 * @param id: id of component to get
	 * @param clazz: class of component to get
	 * @param version: version of the component to get (0 to get the latest version) 
	 * @return HashMap containing the object data
	 * @throws Exception
	 */
	public HashMap<String, Object> getObject(String id, String clazz, int objectVersion) throws Exception {
		ResultSet result = null;
		int version = objectVersion;
		HashMap<String, Object> hashResult = null;

		try {
			if ( version == 0 ) {
				// because of PostGreSQL, we need to split the request in two
				if ( DBPlugin.areEqual(clazz,  "IArchimateElement") ) result = select("SELECT id, version, class, name, documentation, type, created_by, created_on, checksum FROM "+this.schema+"elements e WHERE id = ? AND version = (SELECT MAX(version) FROM "+this.schema+"elements WHERE id = e.id)", id);
				else if ( DBPlugin.areEqual(clazz,  "IArchimateRelationship") ) result = select("SELECT id, version, class, name, documentation, source_id, target_id, strength, access_type, created_by, created_on, checksum FROM "+this.schema+"relationships r WHERE id = ? AND version = (SELECT MAX(version) FROM "+this.schema+"relationships WHERE id = r.id)", id);
				else if ( DBPlugin.areEqual(clazz,  "IFolder") ) result = select("SELECT id, version, type, name, documentation, created_by, created_on, checksum FROM folders f WHERE id = ? AND version = (SELECT MAX(version) FROM "+this.schema+"folders WHERE id = f.id)", id);
				else if ( DBPlugin.areEqual(clazz,  "IDiagramModel") ) result = select("SELECT id, version, class, name, documentation, hint_content, hint_title, created_by, created_on, background, connection_router_type, viewpoint, checksum, container_checksum FROM "+this.schema+"views v WHERE id = ? AND version = (SELECT MAX(version) FROM "+this.schema+"views WHERE id = v.id)", id);
				else if ( DBPlugin.areEqual(clazz,  "IDiagramModelArchimateObject") ) result = select("SELECT id, version, class, container_id, element_id, diagram_ref_id, border_color, border_type, content, documentation, hint_content, hint_title, is_locked, image_path, image_position, line_color, line_width, fill_color, font, font_color, name, notes, text_alignment, text_position, type, x, y, width, height, created_by, created_on, checksum FROM "+this.schema+"views_objects v WHERE id = ? AND version = (SELECT MAX(version) FROM "+this.schema+"views_objects WHERE id = v.id)", id);
				else if ( DBPlugin.areEqual(clazz,  "IDiagramModelArchimateConnection") ) result = select("SELECT id, version, class, container_id, name, documentation, is_locked, line_color, line_width, font, font_color, relationship_id, relationship_version, source_object_id, target_object_id, text_position, type, created_by, created_on, checksum FROM "+this.schema+"views_connections v WHERE id = ? AND version = (SELECT MAX(version) FROM "+this.schema+"views_connections WHERE id = v.id)", id);
				else throw new Exception("Do not know how to get a "+clazz+" from the database.");
			} else {        
				if ( DBPlugin.areEqual(clazz,  "IArchimateElement") ) result = select("SELECT id, version, class, name, documentation, type, created_by, created_on, checksum FROM "+this.schema+"elements WHERE id = ? AND version = ?", id, version);
				else if ( DBPlugin.areEqual(clazz,  "IArchimateRelationship") ) result = select("SELECT id, version, class, name, documentation, source_id, target_id, strength, access_type, created_by, created_on, checksum FROM "+this.schema+"relationships WHERE id = ? AND version = ?", id, version);
				else if ( DBPlugin.areEqual(clazz,  "IFolder") ) result = select("SELECT id, version, type, name, documentation, created_by, created_on, checksum FROM "+this.schema+"folders WHERE id = ? AND version = ?", id, version);
				else if ( DBPlugin.areEqual(clazz,  "IDiagramModel") ) result = select("SELECT id, version, class, name, documentation, hint_content, hint_title, created_by, created_on, background, connection_router_type, viewpoint, checksum, container_checksum FROM "+this.schema+"views WHERE id = ? AND version = ?", id, version);
				else if ( DBPlugin.areEqual(clazz,  "IDiagramModelArchimateObject") ) result = select("SELECT id, version, class, container_id, element_id, diagram_ref_id, border_color, border_type, content, documentation, hint_content, hint_title, is_locked, image_path, image_position, line_color, line_width, fill_color, font, font_color, name, notes, text_alignment, text_position, type, x, y, width, height, checksum FROM "+this.schema+"views_objects WHERE id = ? AND version = ?", id, version);
				else if ( DBPlugin.areEqual(clazz,  "IDiagramModelArchimateConnection") ) result = select("SELECT id, version, class, container_id, name, documentation, is_locked, line_color, line_width, font, font_color, relationship_id, relationship_version, source_object_id, target_object_id, text_position, type, created_by, created_on, checksum FROM "+this.schema+"views_connections v WHERE id = ? AND version = ?", id, version);
				else throw new Exception("Do not know how to get a "+clazz+" from the database.");
			}

			if ( result.next() ) {
				version = result.getInt("version");

				hashResult = resultSetToHashMap(result);
				if ( DBPlugin.areEqual(clazz,  "IFolder") ) hashResult.put("class", "Folder");                  // the folders table does not have a class column, so we add the property by hand

				// properties
				String[][] databaseProperties;
				try ( ResultSet resultProperties = select("SELECT count(*) as count_properties FROM "+this.schema+"properties WHERE parent_id = ? AND parent_version = ?", id, version) ) {
					resultProperties.next();
					databaseProperties = new String[resultProperties.getInt("count_properties")][2];
				}

				try ( ResultSet resultProperties = select("SELECT name, value FROM "+this.schema+"properties WHERE parent_id = ? AND parent_version = ? ORDER BY RANK", id, version) ) {
					int i = 0;
					while ( resultProperties.next() ) {
						databaseProperties[i++] = new String[] { resultProperties.getString("name"), resultProperties.getString("value") };
					}
					hashResult.put("properties", databaseProperties);
				}

				// bendpoints
				Integer[][] databaseBendpoints;
				try ( ResultSet resultBendpoints = select("SELECT count(*) as count_bendpoints FROM "+this.schema+"bendpoints WHERE parent_id = ? AND parent_version = ?", id, version) ) {
					result.next();
					databaseBendpoints = new Integer[resultBendpoints.getInt("count_bendpoints")][4];
				}

				try ( ResultSet resultBendpoints = select("SELECT start_x, start_y, end_x, end_y FROM "+this.schema+"bendpoints WHERE parent_id = ? AND parent_version = ? ORDER BY RANK", id, version ) ) {
					int j = 0;
					while ( result.next() ) {
						databaseBendpoints[j++] = new Integer[] { resultBendpoints.getInt("start_x"), resultBendpoints.getInt("start_y"), resultBendpoints.getInt("end_x"), resultBendpoints.getInt("end_y") };
					}
					hashResult.put("bendpoints", databaseBendpoints);
				}
			} else
				hashResult = new HashMap<String, Object>();
		} finally {
			if ( result != null ) {
				result.close();
				result = null;
			}
		}

		return hashResult;
	}

	/**
	 * Creates a HashMap from a ResultSet
	 */
	public static HashMap<String, Object> resultSetToHashMap(ResultSet rs) throws SQLException {
		HashMap<String, Object> map = new HashMap<String, Object>();

		for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++) {
			if ( rs.getObject(column) != null ) {
				// we only listed the types that may be found by the database proxy and not the exhaustive types list
				String columnName = rs.getMetaData().getColumnName(column).toLowerCase();			// we need to convert to lowercase because of Oracle
				switch ( rs.getMetaData().getColumnType(column) ) {
					case Types.INTEGER :
					case Types.NUMERIC :
					case Types.SMALLINT :
					case Types.TINYINT :
					case Types.BIGINT :
					case Types.BOOLEAN :
					case Types.BIT :        map.put(columnName, rs.getInt(column));  break;

					case Types.TIMESTAMP :
					case Types.TIME :       map.put(columnName, rs.getTimestamp(column)); break;

					default :               map.put(columnName, rs.getString(column));
				}
			}
		}

		return map;
	}

	@Getter private HashSet<String> allImagePaths = new HashSet<String>();

	@Getter private int countElementsToImport = 0;
	@Getter private int countElementsImported = 0;
	@Getter private int countRelationshipsToImport = 0;
	@Getter private int countRelationshipsImported = 0;
	@Getter private int countFoldersToImport = 0;
	@Getter private int countFoldersImported = 0;
	@Getter private int countViewsToImport = 0;
	@Getter private int countViewsImported = 0;
	@Getter private int countViewObjectsToImport = 0;
	@Getter private int countViewObjectsImported = 0;
	@Getter private int countViewConnectionsToImport = 0;
	@Getter private int countViewConnectionsImported = 0;
	@Getter private int countImagesToImport = 0;
	@Getter private int countImagesImported = 0;

	private String importElementsRequest;
	private String importRelationshipsRequest;
	private String importFoldersRequest;
	private String importViewsRequest;

	/**
	 * Import the model metadata from the database
	 */
	public int importModel(DBArchimateModel model) throws Exception {
		// reseting the model's counters
		model.resetCounters();

		if ( model.getInitialVersion().getVersion() == 0 ) {
			try ( ResultSet result = select("SELECT MAX(version) FROM "+this.schema+"models WHERE id = ?", model.getId()) ) {
				if ( result.next() )
					model.getInitialVersion().setVersion(result.getInt("version"));
			}
		}

		try ( ResultSet result = select("SELECT name, purpose, checksum, created_on FROM "+this.schema+"models WHERE id = ? AND version = ?", model.getId(), model.getInitialVersion().getVersion()) ) {
			result.next();
			model.setPurpose(result.getString("purpose"));
			model.getInitialVersion().setChecksum(result.getString("checksum"));
			model.getInitialVersion().setTimestamp(result.getTimestamp("created_on"));
		}

		importProperties(model);
		importMetadata(model);

		String toCharDocumentation = DBPlugin.areEqual(this.databaseEntry.getDriver(), DBDatabase.ORACLE.getDriverName()) ? "TO_CHAR(documentation)" : "documentation";
		String toCharDocumentationAsDocumentation = DBPlugin.areEqual(this.databaseEntry.getDriver(), DBDatabase.ORACLE.getDriverName()) ? "TO_CHAR(documentation) AS documentation" : "documentation";


		String versionToImport = model.isLatestVersionImported() ? "(SELECT MAX(version) FROM "+this.schema+"elements WHERE id = element_id)" : "element_version";
		this.importElementsRequest = "SELECT DISTINCT element_id, parent_folder_id, version, class, name, type, "+toCharDocumentationAsDocumentation+", created_on, checksum"
				+ " FROM "+this.schema+"elements_in_model"
				+ " JOIN "+this.schema+"elements ON elements.id = element_id AND version = "+versionToImport
				+ " WHERE model_id = ? AND model_version = ?"
				+ " GROUP BY element_id, parent_folder_id, version, class, name, type, "+toCharDocumentation+", created_on, checksum";
		try (ResultSet resultElements = select("SELECT COUNT(*) AS countElements FROM ("+this.importElementsRequest+") elts", model.getId(), model.getInitialVersion().getVersion()) ) {
			resultElements.next();
			this.countElementsToImport = resultElements.getInt("countElements");
			this.countElementsImported = 0;
		}


		versionToImport = model.isLatestVersionImported() ? "(SELECT MAX(version) FROM "+this.schema+"relationships WHERE id = relationship_id)" : "relationship_version";
		this.importRelationshipsRequest = "SELECT relationship_id, parent_folder_id, version, class, name, "+toCharDocumentationAsDocumentation+", source_id, target_id, strength, access_type, created_on, checksum"
				+ " FROM "+this.schema+"relationships_in_model"
				+ " INNER JOIN "+this.schema+"relationships ON id = relationship_id AND version = "+versionToImport
				+ " WHERE model_id = ? AND model_version = ?"
				+ " GROUP BY relationship_id, parent_folder_id, version, class, name, "+toCharDocumentation+", source_id, target_id, strength, access_type, created_on, checksum";
		try ( ResultSet resultRelationships = select("SELECT COUNT(*) AS countRelationships FROM ("+this.importRelationshipsRequest+") relts"
				,model.getId()
				,model.getInitialVersion().getVersion()
				) ) {
			resultRelationships.next();
			this.countRelationshipsToImport = resultRelationships.getInt("countRelationships");
			this.countRelationshipsImported = 0;
		}

		versionToImport = model.isLatestVersionImported() ? "(SELECT MAX(version) FROM "+this.schema+"folders WHERE folders.id = folders_in_model.folder_id)" : "folders_in_model.folder_version";
		String selectFoldersRequest = "SELECT folder_id, folder_version, parent_folder_id, type, root_type, name, documentation, created_on, checksum"
				+ " FROM "+this.schema+"folders_in_model"
				+ " JOIN "+this.schema+"folders ON folders.id = folders_in_model.folder_id AND folders.version = "+versionToImport
				+ " WHERE model_id = ? AND model_version = ?";
		try ( ResultSet resultFolders = select("SELECT COUNT(*) AS countFolders FROM ("+selectFoldersRequest+") fldrs", model.getId(), model.getInitialVersion().getVersion()) ) {
			resultFolders.next();
			this.countFoldersToImport = resultFolders.getInt("countFolders");
			this.countFoldersImported = 0;
		}
		this.importFoldersRequest = selectFoldersRequest + " ORDER BY folders_in_model.rank";				// we need to put aside the ORDER BY from the SELECT FROM SELECT because of SQL Server

		versionToImport = model.isLatestVersionImported() ? "(select max(version) from "+this.schema+"views where views.id = views_in_model.view_id)" : "views_in_model.view_version";
		String selectViewsRequest = "SELECT id, version, parent_folder_id, class, name, documentation, background, connection_router_type, hint_content, hint_title, viewpoint, created_on, checksum, container_checksum"
				+ " FROM "+this.schema+"views_in_model"
				+ " JOIN "+this.schema+"views ON views.id = views_in_model.view_id AND views.version = "+versionToImport
				+ " WHERE model_id = ? AND model_version = ?";
		try ( ResultSet resultViews = select("SELECT COUNT(*) AS countViews FROM ("+selectViewsRequest+") vws", model.getId(), model.getInitialVersion().getVersion()) ) {
			resultViews.next();
			this.countViewsToImport = resultViews.getInt("countViews");
			this.countViewsImported = 0;
		}
		this.importViewsRequest = selectViewsRequest + " ORDER BY views_in_model.rank";				// we need to put aside the ORDER BY from the SELECT FROM SELECT because of SQL Server

		// versionToImport is same as for views
		String selectViewsObjectsRequest = "SELECT id, version, class, container_id, element_id, diagram_ref_id, border_color, border_type, content, documentation, hint_content, hint_title, is_locked, image_path, image_position, line_color, line_width, fill_color, font, font_color, name, notes, text_alignment, text_position, type, x, y, width, height, checksum"
				+ " FROM "+this.schema+"views_objects"
				+ " JOIN "+this.schema+"views_objects_in_view ON views_objects_in_view.object_id = views_objects.id AND views_objects_in_view.object_version = views_objects.version"
				+ " JOIN "+this.schema+"views_in_model ON views_objects_in_view.view_id = views_in_model.view_id AND views_objects_in_view.view_version = "+versionToImport
				+ " WHERE model_id = ? AND model_version = ?";
		try ( ResultSet resultViewObjects = select("SELECT COUNT(*) AS countViewsObjects FROM ("+selectViewsObjectsRequest+") vobjs", model.getId(), model.getInitialVersion().getVersion()) ) {
			resultViewObjects.next();
			this.countViewObjectsToImport = resultViewObjects.getInt("countViewsObjects");
			this.countViewObjectsImported = 0;
		}
		// (unused) this.importViewsObjectsRequest = this.selectViewsObjectsRequest + " ORDER BY views_objects.rank";				// we need to put aside the ORDER BY from the SELECT FROM SELECT because of SQL Server

		// versionToImport is same as for views
		String selectViewsConnectionsRequest = "SELECT id, version, class, container_id, name, documentation, is_locked, line_color, line_width, font, font_color, relationship_id, source_object_id, target_object_id, text_position, type, checksum "
				+ " FROM "+this.schema+"views_connections"
				+ " JOIN "+this.schema+"views_connections_in_view ON views_connections_in_view.connection_id = views_connections.id AND views_connections_in_view.connection_version = views_connections.version"
				+ " JOIN "+this.schema+"views_in_model ON views_connections_in_view.view_id = views_in_model.view_id AND views_connections_in_view.view_version = "+versionToImport
				+ " WHERE model_id = ? AND model_version = ?";
		try ( ResultSet resultViewConnections = select("SELECT COUNT(*) AS countViewsConnections FROM ("+selectViewsConnectionsRequest+") vcons", model.getId(), model.getInitialVersion().getVersion()) ) {
			resultViewConnections.next();
			this.countViewConnectionsToImport = resultViewConnections.getInt("countViewsConnections");
			this.countViewConnectionsImported = 0;
		}
		// (unused) this.importViewsConnectionsRequest = this.selectViewsConnectionsRequest + " ORDER BY views_connections.rank";				// we need to put aside the ORDER BY from the SELECT FROM SELECT because of SQL Server

		try ( ResultSet resultImages = select("SELECT COUNT(DISTINCT image_path) AS countImages"+
				" FROM "+this.schema+"views_in_model"+
				" INNER JOIN "+this.schema+"views ON views_in_model.view_id = views.id AND views_in_model.view_version = views.version"+
				" INNER JOIN "+this.schema+"views_objects_in_view ON views_objects_in_view.view_id = views.id AND views_objects_in_view.view_version = views.version"+
				" INNER JOIN "+this.schema+"views_objects ON views_objects.id = views_objects_in_view.object_id AND views_objects.version = views_objects_in_view.object_version"+
				" WHERE model_id = ? AND model_version = ? AND image_path IS NOT NULL" 
				,model.getId()
				,model.getInitialVersion().getVersion()
				))
		{
			resultImages.next();
			this.countImagesToImport = resultImages.getInt("countImages");
			this.countImagesImported = 0;
		}

		if ( logger.isDebugEnabled() ) logger.debug("Importing "+this.countElementsToImport+" elements, "+this.countRelationshipsToImport+" relationships, "+this.countFoldersToImport+" folders, "+this.countViewsToImport+" views, "+this.countViewObjectsToImport+" views objects, "+this.countViewConnectionsToImport+" views connections, and "+this.countImagesToImport+" images.");

		// initializing the HashMaps that will be used to reference imported objects
		this.allImagePaths.clear();

		return this.countElementsToImport + this.countRelationshipsToImport + this.countFoldersToImport + this.countViewsToImport + this.countViewObjectsToImport + this.countViewConnectionsToImport + this.countImagesToImport;
	}

	/**
	 * Prepare the import of the folders from the database
	 */
	public void prepareImportFolders(DBArchimateModel model) throws Exception {
		this.currentResultSet = select(this.importFoldersRequest
				,model.getId()
				,model.getInitialVersion().getVersion()
				);
	}

	/**
	 * Import the folders from the database
	 */
	public boolean importFolders(DBArchimateModel model) throws Exception {
		if ( this.currentResultSet != null ) {
			if ( this.currentResultSet.next() ) {
				IFolder folder = DBArchimateFactory.eINSTANCE.createFolder();
				DBMetadata metadata = ((IDBMetadata)folder).getDBMetadata();

				folder.setId(this.currentResultSet.getString("folder_id"));
				
				metadata.getInitialVersion().setVersion(this.currentResultSet.getInt("folder_version"));
				metadata.getInitialVersion().setChecksum(this.currentResultSet.getString("checksum"));
				metadata.getInitialVersion().setTimestamp(this.currentResultSet.getTimestamp("created_on"));

				metadata.setName(this.currentResultSet.getString("name"));
				metadata.setDocumentation(this.currentResultSet.getString("documentation"));

				String parentId = this.currentResultSet.getString("parent_folder_id");

				if ( parentId != null && !parentId.isEmpty() ) {
					metadata.setType(FolderType.get(0));                              		// non root folders have got the "USER" type

					IFolder parent = model.getAllFolders().get(parentId);
					if ( parent == null )
						parent=model.getFolder(FolderType.get(this.currentResultSet.getInt("root_type")));
					if ( parent == null ) 
						throw new Exception("Don't know where to create folder "+this.currentResultSet.getString("name")+" of type "+this.currentResultSet.getInt("type")+" and root_type "+this.currentResultSet.getInt("root_type")+" (unknown folder ID "+this.currentResultSet.getString("parent_folder_id")+")");

					parent.getFolders().add(folder);
				} else {
					metadata.setType(FolderType.get(this.currentResultSet.getInt("type")));        // root folders have got their own type
					model.getFolders().add(folder);
				}

				importProperties(folder);
				if ( logger.isDebugEnabled() ) logger.debug("   imported version "+((IDBMetadata)folder).getDBMetadata().getInitialVersion().getVersion()+" of "+((IDBMetadata)folder).getDBMetadata().getDebugName());

				// we reference this folder for future use (storing sub-folders or components into it ...)
				model.countObject(folder, false, null);
				++this.countFoldersImported;
				return true;
			}
			this.currentResultSet.close();
			this.currentResultSet = null;
		}
		return false;
	}

	/**
	 * Prepare the import of the elements from the database
	 */
	public void prepareImportElements(DBArchimateModel model) throws Exception {
		this.currentResultSet = select(this.importElementsRequest
				,model.getId()
				,model.getInitialVersion().getVersion()
				);

	}

	/**
	 * import the elements from the database
	 */
	public boolean importElements(DBArchimateModel model) throws Exception {
		if ( this.currentResultSet != null ) {
			if ( this.currentResultSet.next() ) {
				IArchimateElement element = (IArchimateElement) DBArchimateFactory.eINSTANCE.create(this.currentResultSet.getString("class"));
				DBMetadata metadata = ((IDBMetadata)element).getDBMetadata();
				
				element.setId(this.currentResultSet.getString("element_id"));
				
				metadata.getInitialVersion().setVersion(this.currentResultSet.getInt("version"));
				metadata.getInitialVersion().setChecksum(this.currentResultSet.getString("checksum"));
				metadata.getInitialVersion().setTimestamp(this.currentResultSet.getTimestamp("created_on"));

				metadata.setName(this.currentResultSet.getString("name"));
				metadata.setDocumentation(this.currentResultSet.getString("documentation"));
				metadata.setType(this.currentResultSet.getString("type"));

				IFolder folder;
				if ( this.currentResultSet.getString("parent_folder_id") == null ) {
					folder = model.getDefaultFolderForObject(element);
				} else {
					folder = model.getAllFolders().get(this.currentResultSet.getString("parent_folder_id"));
				}
				folder.getElements().add(element);

				importProperties(element);

				if ( logger.isDebugEnabled() ) logger.debug("   imported version "+((IDBMetadata)element).getDBMetadata().getInitialVersion().getVersion()+" of "+((IDBMetadata)element).getDBMetadata().getDebugName());

				// we reference the element for future use (establishing relationships, creating views objects, ...)
				model.countObject(element, false, null);
				++this.countElementsImported;
				return true;
			}
			this.currentResultSet.close();
			this.currentResultSet = null;
		}
		return false;
	}

	/**
	 * Prepare the import of the relationships from the database
	 */
	public void prepareImportRelationships(DBArchimateModel model) throws Exception {
		this.currentResultSet = select(this.importRelationshipsRequest
				,model.getId()
				,model.getInitialVersion().getVersion()
				);
	}

	/**
	 * import the relationships from the database
	 */
	public boolean importRelationships(DBArchimateModel model) throws Exception {
		if ( this.currentResultSet != null ) {
			if ( this.currentResultSet.next() ) {
				IArchimateRelationship relationship = (IArchimateRelationship) DBArchimateFactory.eINSTANCE.create(this.currentResultSet.getString("class"));
				DBMetadata metadata = ((IDBMetadata)relationship).getDBMetadata();
				
				relationship.setId(this.currentResultSet.getString("relationship_id"));
				
				metadata.getInitialVersion().setVersion(this.currentResultSet.getInt("version"));
				metadata.getInitialVersion().setChecksum(this.currentResultSet.getString("checksum"));
				metadata.getInitialVersion().setTimestamp(this.currentResultSet.getTimestamp("created_on"));

				metadata.setName(this.currentResultSet.getString("name")==null ? "" : this.currentResultSet.getString("name"));
				metadata.setDocumentation(this.currentResultSet.getString("documentation"));
				metadata.setStrength(this.currentResultSet.getString("strength"));
				metadata.setAccessType(this.currentResultSet.getInt("access_type"));

				IFolder folder;
				if ( this.currentResultSet.getString("parent_folder_id") == null ) {
					folder = model.getDefaultFolderForObject(relationship);
				} else {
					folder = model.getAllFolders().get(this.currentResultSet.getString("parent_folder_id"));
				}
				folder.getElements().add(relationship);
				
                IArchimateConcept source = model.getAllElements().get(this.currentResultSet.getString("source_id"));
                IArchimateConcept target = model.getAllElements().get(this.currentResultSet.getString("target_id"));
                
                if ( source != null ) {
                    // source is an element and is reputed already imported, so we can set it right away
                    relationship.setSource(source);
                    source.getSourceRelationships().add(relationship);
                } else {
                    // source is another connection and may not be already loaded. So we register it for future resolution
                    model.registerSourceRelationship(relationship, this.currentResultSet.getString("source_id"));
                }
                
                if ( target != null ) {
                    // target is an element and is reputed already imported, so we can set it right away
                    relationship.setTarget(target);
                    target.getTargetRelationships().add(relationship);
                } else {
                    // target is another connection and may not be already loaded. So we register it for future resolution
                    model.registerTargetRelationship(relationship, this.currentResultSet.getString("target_id"));
                }

				importProperties(relationship);

				if ( logger.isDebugEnabled() ) logger.debug("   imported version "+((IDBMetadata)relationship).getDBMetadata().getInitialVersion().getVersion()+" of "+((IDBMetadata)relationship).getDBMetadata().getDebugName());

				model.countObject(relationship, false, null);

				++this.countRelationshipsImported;
				return true;
			}
			this.currentResultSet.close();
			this.currentResultSet = null;
		}
		return false;
	}

	/**
	 * Prepare the import of the views from the database
	 */
	public void prepareImportViews(DBArchimateModel model) throws Exception {
		this.currentResultSet = select(this.importViewsRequest,
				model.getId(),
				model.getInitialVersion().getVersion()
				);
	}

	/**
	 * import the views from the database
	 */
	public boolean importViews(DBArchimateModel model) throws Exception {
		if ( this.currentResultSet != null ) {
			if ( this.currentResultSet.next() ) {
				IDiagramModel view;
				if ( DBPlugin.areEqual(this.currentResultSet.getString("class"), "CanvasModel") )
					view = (IDiagramModel) DBCanvasFactory.eINSTANCE.create(this.currentResultSet.getString("class"));
				else
					view = (IDiagramModel) DBArchimateFactory.eINSTANCE.create(this.currentResultSet.getString("class"));

				DBMetadata metadata = ((IDBMetadata)view).getDBMetadata();
				
				view.setId(this.currentResultSet.getString("id"));
				
				metadata.getInitialVersion().setVersion(this.currentResultSet.getInt("version"));
				metadata.getInitialVersion().setChecksum(this.currentResultSet.getString("checksum"));
				metadata.getInitialVersion().setContainerChecksum(this.currentResultSet.getString("container_checksum"));
				metadata.getInitialVersion().setTimestamp(this.currentResultSet.getTimestamp("created_on"));

				metadata.setName(this.currentResultSet.getString("name"));
				metadata.setDocumentation(this.currentResultSet.getString("documentation"));
				metadata.setConnectionRouterType(this.currentResultSet.getInt("connection_router_type"));
				metadata.setViewpoint(this.currentResultSet.getString("viewpoint"));
				metadata.setBackground(this.currentResultSet.getInt("background"));
				metadata.setHintContent(this.currentResultSet.getString("hint_content"));
				metadata.setHintTitle(this.currentResultSet.getString("hint_title"));

				model.getAllFolders().get(this.currentResultSet.getString("parent_folder_id")).getElements().add(view);

				importProperties(view);

				if ( logger.isDebugEnabled() ) logger.debug("   imported version "+((IDBMetadata)view).getDBMetadata().getInitialVersion().getVersion()+" of "+((IDBMetadata)view).getDBMetadata().getDebugName());

				// we reference the view for future use
				model.countObject(view, false, null);
				++this.countViewsImported;
				return true;
			}
			this.currentResultSet.close();
			this.currentResultSet = null;
		}
		return false;
	}

	/**
	 * Prepare the import of the views objects of a specific view from the database
	 */
	public void prepareImportViewsObjects(String id, int version) throws Exception {
		this.currentResultSet = select("SELECT id, version, class, container_id, element_id, diagram_ref_id, border_color, border_type, content, documentation, hint_content, hint_title, is_locked, image_path, image_position, line_color, line_width, fill_color, font, font_color, name, notes, text_alignment, text_position, type, x, y, width, height, checksum, created_on"
				+" FROM "+this.schema+"views_objects"
				+" JOIN "+this.schema+"views_objects_in_view ON views_objects_in_view.object_id = views_objects.id AND views_objects_in_view.object_version = views_objects.version"
				+" WHERE view_id = ? AND view_version = ?"
				+" ORDER BY rank"
				,id
				,version
				);
	}

	/**
	 * import the views objects from the database
	 */
	public boolean importViewsObjects(DBArchimateModel model, IDiagramModel view) throws Exception {
		if ( this.currentResultSet != null ) {
			if ( this.currentResultSet.next() ) {
				EObject eObject;

				if ( this.currentResultSet.getString("class").startsWith("Canvas") )
					eObject = DBCanvasFactory.eINSTANCE.create(this.currentResultSet.getString("class"));
				else
					eObject = DBArchimateFactory.eINSTANCE.create(this.currentResultSet.getString("class"));
				
				DBMetadata metadata = ((IDBMetadata)eObject).getDBMetadata();

				((IIdentifier)eObject).setId(this.currentResultSet.getString("id"));
				
				metadata.getInitialVersion().setVersion(this.currentResultSet.getInt("version"));
				metadata.getInitialVersion().setChecksum(this.currentResultSet.getString("checksum"));
				metadata.getInitialVersion().setTimestamp(this.currentResultSet.getTimestamp("created_on"));

				if ( eObject instanceof IDiagramModelArchimateComponent && this.currentResultSet.getString("element_id") != null) {
					// we check that the element already exists. If not, we import it (this may be the case during an individual view import.
					IArchimateElement element = model.getAllElements().get(this.currentResultSet.getString("element_id"));
					if ( element == null )
						importElementFromId(model, null, this.currentResultSet.getString("element_id"), 0, false, true);
				}

				metadata.setArchimateConcept(model.getAllElements().get(this.currentResultSet.getString("element_id")));
				metadata.setReferencedModel(model.getAllViews().get(this.currentResultSet.getString("diagram_ref_id")));
				metadata.setType(this.currentResultSet.getInt("type"));
				metadata.setBorderColor(this.currentResultSet.getString("border_color"));
				metadata.setBorderType(this.currentResultSet.getInt("border_type"));
				metadata.setContent(this.currentResultSet.getString("content"));
				metadata.setDocumentation(this.currentResultSet.getString("documentation"));
				metadata.setName(this.currentResultSet.getString("name"));
				metadata.setHintContent(this.currentResultSet.getString("hint_content"));
				metadata.setHintTitle(this.currentResultSet.getString("hint_title"));
				metadata.setLocked(this.currentResultSet.getObject("is_locked"));
				metadata.setImagePath(this.currentResultSet.getString("image_path"));
				metadata.setImagePosition(this.currentResultSet.getInt("image_position"));
				metadata.setLineColor(this.currentResultSet.getString("line_color"));
				metadata.setLineWidth(this.currentResultSet.getInt("line_width"));
				metadata.setFillColor(this.currentResultSet.getString("fill_color"));
				metadata.setFont(this.currentResultSet.getString("font"));
				metadata.setFontColor(this.currentResultSet.getString("font_color"));
				metadata.setNotes(this.currentResultSet.getString("notes"));
				metadata.setTextAlignment(this.currentResultSet.getInt("text_alignment"));
				metadata.setTextPosition(this.currentResultSet.getInt("text_position"));
				metadata.setBounds(this.currentResultSet.getInt("x"), this.currentResultSet.getInt("y"), this.currentResultSet.getInt("width"), this.currentResultSet.getInt("height"));

				// The container is either the view, or a container in the view
				if ( DBPlugin.areEqual(this.currentResultSet.getString("container_id"), view.getId()) )
					view.getChildren().add((IDiagramModelObject)eObject);
				else
					((IDiagramModelContainer)model.getAllViewObjects().get(this.currentResultSet.getString("container_id"))).getChildren().add((IDiagramModelObject)eObject);

				// If the object has got properties but does not have a linked element, then it may have distinct properties
				if ( eObject instanceof IProperties && this.currentResultSet.getString("element_id")==null ) {
					importProperties((IProperties)eObject);
				}

				if ( logger.isDebugEnabled() ) logger.debug("   imported version "+((IDBMetadata)eObject).getDBMetadata().getInitialVersion().getVersion()+" of "+((IDBMetadata)eObject).getDBMetadata().getDebugName());

				// we reference the view for future use
				model.countObject(eObject, false, null);
				++this.countViewObjectsImported;

				// if the object contains an image, we store its path to import it later
				if ( this.currentResultSet.getString("image_path") != null )
					this.allImagePaths.add(this.currentResultSet.getString("image_path"));

				return true;
			}
			this.currentResultSet.close();
			this.currentResultSet = null;
		}
		return false;
	}

	/**
	 * Prepare the import of the views connections of a specific view from the database
	 */
	public void prepareImportViewsConnections(String id, int version) throws Exception {
		this.currentResultSet = select("SELECT id, version, class, container_id, name, documentation, is_locked, line_color, line_width, font, font_color, relationship_id, source_object_id, target_object_id, text_position, type, checksum"
				+" FROM "+this.schema+"views_connections"
				+" JOIN "+this.schema+"views_connections_in_view ON views_connections_in_view.connection_id = views_connections.id AND views_connections_in_view.connection_version = views_connections.version"
				+" WHERE view_id = ? AND view_version = ?"
				+" ORDER BY rank"
				,id
				,version
				);
	}

	/**
	 * import the views connections from the database
	 */
	public boolean importViewsConnections(DBArchimateModel model) throws Exception {
		if ( this.currentResultSet != null ) {
			if ( this.currentResultSet.next() ) {
				EObject eObject;

				if ( this.currentResultSet.getString("class").startsWith("Canvas") )
					eObject = DBCanvasFactory.eINSTANCE.create(this.currentResultSet.getString("class"));
				else
					eObject = DBArchimateFactory.eINSTANCE.create(this.currentResultSet.getString("class"));
				
				DBMetadata metadata = ((IDBMetadata)eObject).getDBMetadata();

				((IIdentifier)eObject).setId(this.currentResultSet.getString("id"));
				
				metadata.getInitialVersion().setVersion(this.currentResultSet.getInt("version"));
				metadata.getInitialVersion().setChecksum(this.currentResultSet.getString("checksum"));

				/*
				if ( eObject instanceof IDiagramModelArchimateConnection && this.currentResultSet.getString("relationship_id") != null) {
					// we check that the relationship already exists. If not, we import it (this may be the case during an individual view import.
					IArchimateRelationship relationship = model.getAllRelationships().get(this.currentResultSet.getString("relationship_id"));
					if ( relationship == null ) {
						importRelationshipFromId(model, null, this.currentResultSet.getString("relationship_id"), 0, false);
					}
				}
				*/
				metadata.setName(this.currentResultSet.getString("name"));
				metadata.setLocked(this.currentResultSet.getObject("is_locked"));
				metadata.setDocumentation(this.currentResultSet.getString("documentation"));
				metadata.setLineColor(this.currentResultSet.getString("line_color"));
				metadata.setLineWidth(this.currentResultSet.getInt("line_width"));
				metadata.setFont(this.currentResultSet.getString("font"));
				metadata.setFontColor(this.currentResultSet.getString("font_color"));
				metadata.setType(this.currentResultSet.getInt("type"));
				metadata.setTextPosition(this.currentResultSet.getInt("text_position"));
				metadata.setArchimateConcept(model.getAllRelationships().get(this.currentResultSet.getString("relationship_id")));

				if ( eObject instanceof IDiagramModelConnection ) {
				    IConnectable source = model.getAllViewObjects().get(this.currentResultSet.getString("source_object_id"));
				    IConnectable target = model.getAllViewObjects().get(this.currentResultSet.getString("target_object_id"));
				    
				    if ( source != null ) {
				        // source is an object and is reputed already imported, so we can set it right away
                        ((IDiagramModelConnection)eObject).setSource(source);
                        source.addConnection((IDiagramModelConnection)eObject);
				    } else {
				        // source is another connection and may not be already loaded. So we register it for future resolution
				        model.registerSourceConnection((IDiagramModelConnection)eObject, this.currentResultSet.getString("source_object_id"));
				    }
				    
                    if ( target != null ) {
                        // target is an object and is reputed already imported, so we can set it right away
                        ((IDiagramModelConnection)eObject).setTarget(target);
                        target.addConnection((IDiagramModelConnection)eObject);
                    } else {
                        // target is another connection and may not be already loaded. So we register it for future resolution
                        model.registerTargetConnection((IDiagramModelConnection)eObject, this.currentResultSet.getString("target_object_id"));
                    }
				}

				if ( eObject instanceof IDiagramModelConnection ) {
					try ( ResultSet resultBendpoints = select("SELECT start_x, start_y, end_x, end_y FROM "+this.schema+"bendpoints WHERE parent_id = ? AND parent_version = ? ORDER BY rank", ((IIdentifier)eObject).getId(), ((IDBMetadata)eObject).getDBMetadata().getInitialVersion().getVersion()) ) {
						while(resultBendpoints.next()) {
							IDiagramModelBendpoint bendpoint = DBArchimateFactory.eINSTANCE.createDiagramModelBendpoint();
							bendpoint.setStartX(resultBendpoints.getInt("start_x"));
							bendpoint.setStartY(resultBendpoints.getInt("start_y"));
							bendpoint.setEndX(resultBendpoints.getInt("end_x"));
							bendpoint.setEndY(resultBendpoints.getInt("end_y"));
							((IDiagramModelConnection)eObject).getBendpoints().add(bendpoint);
						}
					}
				}

				// we reference the connection for future use
				model.countObject(eObject, false, null);
				++this.countViewConnectionsImported;

				// If the connection has got properties but does not have a linked relationship, then it may have distinct properties
				if ( eObject instanceof IProperties && this.currentResultSet.getString("relationship_id")==null ) {
					importProperties((IProperties)eObject);
				}

				if ( logger.isDebugEnabled() ) logger.debug("   imported version "+((IDBMetadata)eObject).getDBMetadata().getInitialVersion().getVersion()+" of "+((IDBMetadata)eObject).getDBMetadata().getDebugName());

				return true;
			}
			this.currentResultSet.close();
			this.currentResultSet = null;
		}
		return false;
	}

	/**
	 * import the views from the database
	 */
	public void importImage(DBArchimateModel model, String path) throws Exception {
		try ( ResultSet result = select("SELECT image FROM "+this.schema+"images WHERE path = ?", path) ) {
			if (result.next() ) {
				IArchiveManager archiveMgr = (IArchiveManager)model.getAdapter(IArchiveManager.class);
				try {
					String imagePath;
					byte[] imageContent = result.getBytes("image");

					if ( logger.isDebugEnabled() ) {
						if ( (imageContent.length/1024)/2014 > 1 )
							logger.debug( "Importing "+path+" with "+(imageContent.length/1024)/1024+" Mo of data");
						else
							logger.debug( "Importing "+path+" with "+imageContent.length/1024+" Ko of data");
					}
					imagePath = archiveMgr.addByteContentEntry(path, imageContent);

                    if ( logger.isDebugEnabled() && !DBPlugin.areEqual(imagePath, path) )
                        logger.debug( "... image imported but with new path "+imagePath);

				} catch (Exception e) {
					throw new Exception("Import of image failed !", e.getCause()!=null ? e.getCause() : e);
				}
				++this.countImagesImported;
			}
			else
				throw new Exception("Import of image failed : unkwnown image path "+path);
		} 
	}

	/**
	 * import an image from the database
	 */
	public Image getImageFromDatabase(String path) throws Exception {
		try ( ResultSet result = select("SELECT image FROM "+this.schema+"images WHERE path = ?", path) ) {
			if ( result.next() ) {
				byte[] imageContent = result.getBytes("image");
				if ( logger.isDebugEnabled() ) logger.debug( "Importing "+path+" with "+imageContent.length/1024+" Ko of data");
				return new Image(Display.getDefault(), new ImageData(new ByteArrayInputStream(imageContent)));
			}
		}
		return null;
	}

	/**
	 * gets the list of all images in the database
	 */
	public List<String> getImageListFromDatabase() throws Exception {
		List<String> list = new ArrayList<String>();
		try ( ResultSet result = select("SELECT path FROM "+this.schema+"images") ) {
			while ( result.next() ) {
				list.add(result.getString("path"));
			}
		}
		return list;
	}

	/**
	 * check if the number of imported images is equals to what is expected
	 */
	public void checkImportedImagesCount() throws Exception {
		if ( this.countImagesImported != this.countImagesToImport )
			throw new Exception(this.countImagesImported+" images imported instead of the "+this.countImagesToImport+" that were expected.");

		if ( logger.isDebugEnabled() ) logger.debug(this.countImagesImported+" images imported.");
	}

	/**
	 * Imports the properties of an Archi component<br>
	 * - missing properties are created
	 * - existing properties are updated with correct values if needed
	 * - existing properties with correct values are left untouched 
	 */
	public void importProperties(IProperties parent) throws Exception {
		int version;
		if ( parent instanceof IArchimateModel )
			version = ((DBArchimateModel)parent).getInitialVersion().getVersion();
		else
			version = ((IDBMetadata)parent).getDBMetadata().getInitialVersion().getVersion();

		importProperties(parent, ((IIdentifier)parent).getId(), version);
	}

	/**
	 * Imports the properties of an Archi component<br>
	 * - missing properties are created
	 * - existing properties are updated with correct values if needed
	 * - existing properties with correct values are left untouched 
	 * @throws SQLException 
	 */
	public void importProperties(IProperties parent, String id, int version) throws SQLException {
		// first, we delete all existing properties
		parent.getProperties().clear();

		// then, we import the properties from the database 
		try ( ResultSet result = select("SELECT name, value FROM "+this.schema+"properties WHERE parent_id = ? AND parent_version = ? ORDER BY rank", id, version)) {
			while ( result.next() ) {
				// if the property already exist, we update its value. If it doesn't, we create it
				IProperty prop = DBArchimateFactory.eINSTANCE.createProperty();
				prop.setKey(result.getString("name"));
				prop.setValue(result.getString("value"));
				parent.getProperties().add(prop);
			}
		}
	}

	/**
	 * Imports the metadata of a model<br>
	 * @throws SQLException 
	 */
	private void importMetadata(DBArchimateModel model) throws SQLException {
		// first, we delete all existing metadata
		model.getMetadata().getEntries().clear();

		// then, we import the metadata from the database 
		try ( ResultSet result = select("SELECT name, value FROM "+this.schema+"metadata WHERE parent_id = ? AND parent_version = ? ORDER BY rank", model.getId(), model.getInitialVersion().getVersion())) {
			while ( result.next() ) {
				// if the property already exist, we update its value. If it doesn't, we create it
				IProperty prop = DBArchimateFactory.eINSTANCE.createProperty();
				prop.setKey(result.getString("name"));
				prop.setValue(result.getString("value"));
				model.getMetadata().getEntries().add(prop);
			}
		}
	}

	/**
	 * Imports a folder into the model<br>
	 * @param model model into which the folder will be imported
	 * @param id id of the folder to import
	 * @param version version of the folder to import (0 if the latest version should be imported)
	 * @return the imported folder
	 * @throws Exception
	 */
	public IFolder importFolderFromId(DBArchimateModel model, String id, int version) throws Exception {
		return this.importFolderFromId(model, id, version, false);
	}

	/**
	 * Imports a folder into the model<br>
	 * The folder is imported empty. Folder content should be imported separately<br>
	 * @param model model into which the element will be imported
	 * @param id id of the folder to import
	 * @param folderVersion version of the folder to import (0 if the latest version should be imported)
	 * @param mustCreateCopy true if a copy must be imported (i.e. if a new id must be generated) or false if the folder should be its original id
	 * @return the imported folder
	 * @throws Exception
	 */
	public IFolder importFolderFromId(DBArchimateModel model, String id, int version, boolean mustCreateCopy) throws Exception {
		IFolder folder;
		boolean hasJustBeenCreated = false;

		if ( logger.isDebugEnabled() ) {
			if ( mustCreateCopy )
				logger.debug("Importing a copy of folder id "+id+".");
			else
				logger.debug("Importing folder id "+id+".");
		}

		String versionString = (version==0) ? "(SELECT MAX(version) FROM "+this.schema+"folders WHERE id = f.id)" : String.valueOf(version);

		try ( ResultSet result = select("SELECT version, type, root_type, name, documentation, checksum, created_on FROM "+this.schema+"folders f WHERE id = ? AND version = "+versionString, id) ) {
			if ( !result.next() ) {
				if ( version == 0 )
					throw new Exception("Folder with id="+id+" has not been found in the database.");
				throw new Exception("Folder with id="+id+" and version="+version+" has not been found in the database.");
			}
			
			DBMetadata metadata;

			if ( mustCreateCopy ) {
				hasJustBeenCreated = true;
				folder = DBArchimateFactory.eINSTANCE.createFolder();
				folder.setId(model.getIDAdapter().getNewID());
				folder.setType(FolderType.get(result.getInt("type")));
				
				metadata = ((IDBMetadata)folder).getDBMetadata();

				metadata.setName(result.getString("name"));
				
				metadata.getInitialVersion().setVersion(0);
				metadata.getInitialVersion().setTimestamp(new Timestamp(Calendar.getInstance().getTime().getTime()));

				importProperties(folder, id, result.getInt("version"));
			} else {
				folder = model.getAllFolders().get(id);
				if ( folder == null ) {
					hasJustBeenCreated = true;
					folder = DBArchimateFactory.eINSTANCE.createFolder();
					folder.setId(id);
					folder.setType(FolderType.get(result.getInt("type")));
				}

				metadata = ((IDBMetadata)folder).getDBMetadata();
				
				metadata.setName(result.getString("name"));
				metadata.getInitialVersion().setVersion(result.getInt("version"));
				metadata.getInitialVersion().setChecksum(result.getString("checksum"));
				metadata.getInitialVersion().setTimestamp(result.getTimestamp("created_on"));
				metadata.getCurrentVersion().setVersion(result.getInt("version"));
				metadata.getCurrentVersion().setChecksum(result.getString("checksum"));
				metadata.getCurrentVersion().setTimestamp(result.getTimestamp("created_on"));
				metadata.getDatabaseVersion().setVersion(result.getInt("version"));
				metadata.getDatabaseVersion().setChecksum(result.getString("checksum"));
				metadata.getDatabaseVersion().setTimestamp(result.getTimestamp("created_on"));
				metadata.getLatestDatabaseVersion().setVersion(result.getInt("version"));
				metadata.getLatestDatabaseVersion().setChecksum(result.getString("checksum"));
				metadata.getLatestDatabaseVersion().setTimestamp(result.getTimestamp("created_on"));
			}

			metadata.setDocumentation(result.getString("documentation"));
			metadata.setRootFolderType(result.getInt("root_type"));
			
			setFolderToLastKnown(model, folder);

			if ( hasJustBeenCreated ) 
				model.countObject(folder, false, null);

			++this.countFoldersImported;
		}

		return folder;
	}

	/**
	 * Imports an element into the model<br>
	 * @param view if a view is provided, then an ArchimateObject will be automatically created
	 * @param elementId id of the element to import
	 * @param elementVersion version of the element to import (0 if the latest version should be imported)
	 * @return the imported element
	 * @throws Exception
	 */
	public IArchimateElement importElementFromId(DBArchimateModel model, String elementId, int elementVersion) throws Exception {
		return this.importElementFromId(model, null, elementId, elementVersion, false, false);
	}

	/**
	 * Imports an element into the model<br>
	 * @param model model into which the element will be imported
	 * @param view if a view is provided, then an ArchimateObject will be automatically created
	 * @param id id of the element to import
	 * @param version version of the element to import (0 if the latest version should be imported)
	 * @param mustCreateCopy true if a copy must be imported (i.e. if a new id must be generated) or false if the element should be its original id
	 * @param mustImportRelationships true if the relationships to and from  the newly created element must be imported as well  
	 * @return the imported element
	 * @throws Exception
	 */
	public IArchimateElement importElementFromId(DBArchimateModel model, IArchimateDiagramModel view, String id, int version, boolean mustCreateCopy, boolean mustImportRelationships) throws Exception {
		IArchimateElement element;
		boolean hasJustBeenCreated = false;

		if ( logger.isDebugEnabled() ) {
			if ( mustCreateCopy )
				logger.debug("Importing a copy of element id "+id+".");
			else
				logger.debug("Importing element id "+id+".");
		}

		// TODO add an option to import elements recursively

		String versionString = (version==0) ? "(SELECT MAX(version) FROM "+this.schema+"elements WHERE id = e.id)" : String.valueOf(version);

		try ( ResultSet result = select("SELECT version, class, name, documentation, type, checksum, created_on FROM "+this.schema+"elements e WHERE id = ? AND version = "+versionString, id) ) {
			if ( !result.next() ) {
				if ( version == 0 )
					throw new Exception("Element with id="+id+" has not been found in the database.");
				throw new Exception("Element with id="+id+" and version="+version+" has not been found in the database.");
			}
			
			DBMetadata metadata;

			if ( mustCreateCopy ) {
				element = (IArchimateElement) DBArchimateFactory.eINSTANCE.create(result.getString("class"));
				element.setId(model.getIDAdapter().getNewID());
				hasJustBeenCreated = true;
				
				metadata = ((IDBMetadata)element).getDBMetadata();

				metadata.setName(result.getString("name"));
				
				metadata.getInitialVersion().setVersion(0);
				metadata.getInitialVersion().setTimestamp(new Timestamp(Calendar.getInstance().getTime().getTime()));
				metadata.getCurrentVersion().setVersion(0);
				metadata.getCurrentVersion().setTimestamp(new Timestamp(Calendar.getInstance().getTime().getTime()));

				importProperties(element, id, result.getInt("version"));
			} else {
				element = model.getAllElements().get(id);
				if ( element == null ) {
					element = (IArchimateElement) DBArchimateFactory.eINSTANCE.create(result.getString("class"));
					element.setId(id);
					hasJustBeenCreated = true;
				}
				
				metadata = ((IDBMetadata)element).getDBMetadata();

				metadata.setName(result.getString("name"));
				metadata.getInitialVersion().setVersion(result.getInt("version"));
				metadata.getInitialVersion().setChecksum(result.getString("checksum"));
				metadata.getInitialVersion().setTimestamp(result.getTimestamp("created_on"));
                metadata.getCurrentVersion().setVersion(result.getInt("version"));
                metadata.getCurrentVersion().setChecksum(result.getString("checksum"));
                metadata.getCurrentVersion().setTimestamp(result.getTimestamp("created_on"));
				metadata.getDatabaseVersion().setVersion(result.getInt("version"));
				metadata.getDatabaseVersion().setChecksum(result.getString("checksum"));
				metadata.getDatabaseVersion().setTimestamp(result.getTimestamp("created_on"));
				metadata.getLatestDatabaseVersion().setVersion(result.getInt("version"));
				metadata.getLatestDatabaseVersion().setChecksum(result.getString("checksum"));
				metadata.getLatestDatabaseVersion().setTimestamp(result.getTimestamp("created_on"));

				importProperties(element);
			}

			metadata.setDocumentation(result.getString("documentation"));
			metadata.setType(result.getString("type"));

			if ( view != null && metadata.componentToConnectable(view).isEmpty() ) {
				view.getChildren().add(ArchimateDiagramModelFactory.createDiagramModelArchimateObject(element));
			}

			if ( hasJustBeenCreated )
				model.countObject(element, false, null);

			++this.countElementsImported;

		}

		if ( mustImportRelationships ) {
			// We import the relationships that source or target the element
			try ( ResultSet resultrelationship = select("SELECT id, source_id, target_id FROM "+this.schema+"relationships WHERE source_id = ? OR target_id = ?", id, id) ) {
				while ( resultrelationship.next() && resultrelationship.getString("id") != null ) {
					// we import only relationships that do not exist
					if ( model.getAllRelationships().get(resultrelationship.getString("id")) == null ) {
						IArchimateElement sourceElement = model.getAllElements().get(resultrelationship.getString("source_id"));
						IArchimateRelationship sourceRelationship = model.getAllRelationships().get(resultrelationship.getString("source_id"));
						IArchimateElement targetElement = model.getAllElements().get(resultrelationship.getString("target_id"));
						IArchimateRelationship targetRelationship = model.getAllRelationships().get(resultrelationship.getString("target_id"));

						// we import only relations when both source and target are in the model
						if ( (sourceElement!=null || sourceRelationship!=null) && (targetElement!=null || targetRelationship!=null) ) {
							importRelationshipFromId(model, view, resultrelationship.getString("id"), 0, false);
						}
					}
				}
			}
		    
			model.resolveSourceRelationships();
			model.resolveTargetRelationships();
		}
		return element;
	}

	/**
	 * Imports a relationship into the model
	 * @param model model into which the relationship will be imported
	 * @param view if a view is provided, then an ArchimateConnection will be automatically created
	 * @param id id of the relationship to import
	 * @param version version of the relationship to import (0 if the latest version should be imported)
	 * @param mustCreateCopy true if a copy must be imported (i.e. if a new id must be generated) or false if the element should kee its original id
	 * @return the imported relationship
	 * @throws Exception
	 */
	public IArchimateRelationship importRelationshipFromId(DBArchimateModel model, IArchimateDiagramModel view, String id, int version, boolean mustCreateCopy) throws Exception {
		boolean hasJustBeenCreated = false;
		IArchimateRelationship relationship;

		if ( logger.isDebugEnabled() ) {
			if ( mustCreateCopy )
				logger.debug("Importing a copy of relationship id "+id+".");
			else
				logger.debug("Importing relationship id "+id+".");
		}

		String versionString = (version==0) ? "(SELECT MAX(version) FROM "+this.schema+"relationships WHERE id = r.id)" : String.valueOf(version);

		try ( ResultSet result = select("SELECT version, class, name, documentation, source_id, target_id, strength, access_type, checksum, created_on FROM "+this.schema+"relationships r WHERE id = ? AND version = "+versionString, id) ) {
			if ( !result.next() ) {
				if ( version == 0 )
					throw new Exception("Relationship with id="+id+" has not been found in the database.");
				throw new Exception("Relationship with id="+id+" and version="+version+" has not been found in the database.");
			}
			
			DBMetadata metadata;

			if ( mustCreateCopy ) {
				relationship = (IArchimateRelationship) DBArchimateFactory.eINSTANCE.create(result.getString("class"));
				relationship.setId(model.getIDAdapter().getNewID());
				hasJustBeenCreated = true;
				
				metadata = ((IDBMetadata)relationship).getDBMetadata();

				metadata.getInitialVersion().setVersion(0);
				metadata.getInitialVersion().setTimestamp(new Timestamp(Calendar.getInstance().getTime().getTime()));
				metadata.getCurrentVersion().setVersion(0);
				metadata.getCurrentVersion().setTimestamp(new Timestamp(Calendar.getInstance().getTime().getTime()));
			} else {
				relationship = model.getAllRelationships().get(id);
				if ( relationship == null ) {
					relationship = (IArchimateRelationship) DBArchimateFactory.eINSTANCE.create(result.getString("class"));
					relationship.setId(id);
					hasJustBeenCreated = true;
				}
				
				metadata = ((IDBMetadata)relationship).getDBMetadata();

				metadata.getInitialVersion().setVersion(result.getInt("version"));
				metadata.getInitialVersion().setChecksum(result.getString("checksum"));
				metadata.getInitialVersion().setTimestamp(result.getTimestamp("created_on"));
				metadata.getCurrentVersion().setVersion(result.getInt("version"));
				metadata.getCurrentVersion().setChecksum(result.getString("checksum"));
				metadata.getCurrentVersion().setTimestamp(result.getTimestamp("created_on"));
				metadata.getDatabaseVersion().setVersion(result.getInt("version"));
				metadata.getDatabaseVersion().setChecksum(result.getString("checksum"));
				metadata.getDatabaseVersion().setTimestamp(result.getTimestamp("created_on"));
				metadata.getLatestDatabaseVersion().setVersion(result.getInt("version"));
				metadata.getLatestDatabaseVersion().setChecksum(result.getString("checksum"));
				metadata.getLatestDatabaseVersion().setTimestamp(result.getTimestamp("created_on"));
			}

			metadata.setName(result.getString("name"));
			metadata.setDocumentation(result.getString("documentation"));
			metadata.setStrength(result.getString("strength"));
			metadata.setAccessType(result.getInt("access_type"));

            IArchimateConcept source = model.getAllElements().get(result.getString("source_id"));
            IArchimateConcept target = model.getAllElements().get(result.getString("target_id"));
            
            if ( source != null ) {
                // source is an element and is reputed already imported, so we can set it right away
                relationship.setSource(source);
                source.getSourceRelationships().add(relationship);
            } else {
                // source is another connection and may not be already loaded. So we register it for future resolution
                model.registerSourceRelationship(relationship, this.currentResultSet.getString("source_id"));
            }
            
            if ( target != null ) {
                // target is an element and is reputed already imported, so we can set it right away
                relationship.setTarget(target);
                target.getTargetRelationships().add(relationship);
            } else {
                // target is another connection and may not be already loaded. So we register it for future resolution
                model.registerTargetRelationship(relationship, this.currentResultSet.getString("target_id"));
            }


			importProperties(relationship);

			// During the import of an individual relationship from the database, we check if objects or connections exist for the source and the target
			// and create the corresponding connections
			// TODO : make an option with this functionality that the user can choose if he want the connections or not
			if ( view != null && metadata.componentToConnectable(view).isEmpty() ) {
				List<IConnectable> sourceConnections = metadata.componentToConnectable(view, relationship.getSource());
				List<IConnectable> targetConnections = metadata.componentToConnectable(view, relationship.getTarget());

				for ( IConnectable sourceConnection: sourceConnections ) {
					for ( IConnectable targetConnection: targetConnections ) {
						IDiagramModelArchimateConnection cnct = ArchimateDiagramModelFactory.createDiagramModelArchimateConnection(relationship);
						cnct.setSource(sourceConnection);
						sourceConnection.getSourceConnections().add(cnct);
						cnct.setTarget(targetConnection);
						targetConnection.getTargetConnections().add(cnct);
					}
				}
			}
		}

		if ( hasJustBeenCreated )
			model.countObject(relationship, false, null);

		++this.countRelationshipsImported;

		return relationship;
	}

	/**
	 * This method imports a view, optionally including all graphical objects and connections and requirements (elements and relationships)<br>
	 * elements and relationships that needed to be imported are located in a folder named by the view
	 */
	public IDiagramModel importViewFromId(DBArchimateModel model, String id, int version, boolean mustCreateCopy, boolean mustImportViewContent) throws Exception {
		IDiagramModel view;
		boolean hasJustBeenCreated = false;

		if ( logger.isDebugEnabled() ) {
			if ( mustCreateCopy )
				logger.debug("Importing a copy of view id "+id);
			else {
				logger.debug("Importing view id "+id);
			}
		}

		// 1 : we create or update the view
		String versionString = (version==0) ? "(SELECT MAX(version) FROM "+this.schema+"views WHERE id = v.id)" : String.valueOf(version);

		try ( ResultSet result = select("SELECT version, class, name, documentation, background, connection_router_type, hint_content, hint_title, viewpoint, checksum, container_checksum, created_on FROM "+this.schema+"views v WHERE id = ? AND version = "+versionString, id) ) {
			if ( !result.next() ) {
				if ( version == 0 )
					throw new Exception("View with id="+id+" has not been found in the database.");
				throw new Exception("View with id="+id+" and version="+version+" has not been found in the database.");
			}
			
			DBMetadata metadata;

			view = model.getAllViews().get(id);
			if ( mustCreateCopy ) {
				if ( DBPlugin.areEqual(result.getString("class"), "CanvasModel") )
					view = (IDiagramModel) DBCanvasFactory.eINSTANCE.create(result.getString("class"));
				else
					view = (IDiagramModel) DBArchimateFactory.eINSTANCE.create(result.getString("class"));
				view.setId(model.getIDAdapter().getNewID());
				hasJustBeenCreated = true;
				
				metadata = ((IDBMetadata)view).getDBMetadata();

				metadata.getInitialVersion().setVersion(0);
				metadata.getInitialVersion().setTimestamp(new Timestamp(Calendar.getInstance().getTime().getTime()));
				metadata.getCurrentVersion().setVersion(0);
				metadata.getCurrentVersion().setTimestamp(new Timestamp(Calendar.getInstance().getTime().getTime()));
			} else {
				view = model.getAllViews().get(id);
				if ( view == null ) {
					if ( DBPlugin.areEqual(result.getString("class"), "CanvasModel") )
						view = (IDiagramModel) DBCanvasFactory.eINSTANCE.create(result.getString("class"));
					else
						view = (IDiagramModel) DBArchimateFactory.eINSTANCE.create(result.getString("class"));
					view.setId(id);
					hasJustBeenCreated = true;
				}
				
				metadata = ((IDBMetadata)view).getDBMetadata();

				metadata.getInitialVersion().setVersion(result.getInt("version"));
				metadata.getInitialVersion().setChecksum(result.getString("checksum"));
				metadata.getInitialVersion().setContainerChecksum(result.getString("container_checksum"));
				metadata.getInitialVersion().setTimestamp(result.getTimestamp("created_on"));
				metadata.getCurrentVersion().setVersion(result.getInt("version"));
				metadata.getCurrentVersion().setChecksum(result.getString("checksum"));
				metadata.getInitialVersion().setContainerChecksum(result.getString("container_checksum"));
				metadata.getCurrentVersion().setTimestamp(result.getTimestamp("created_on"));
				metadata.getDatabaseVersion().setVersion(result.getInt("version"));
				metadata.getDatabaseVersion().setChecksum(result.getString("checksum"));
				metadata.getInitialVersion().setContainerChecksum(result.getString("container_checksum"));
				metadata.getDatabaseVersion().setTimestamp(result.getTimestamp("created_on"));
				metadata.getLatestDatabaseVersion().setVersion(result.getInt("version"));
				metadata.getLatestDatabaseVersion().setChecksum(result.getString("checksum"));
				metadata.getInitialVersion().setContainerChecksum(result.getString("container_checksum"));
				metadata.getLatestDatabaseVersion().setTimestamp(result.getTimestamp("created_on"));
			}

			metadata.setName(result.getString("name"));
			metadata.setDocumentation(result.getString("documentation"));
			metadata.setConnectionRouterType(result.getInt("connection_router_type"));

			metadata.setViewpoint(result.getString("viewpoint"));
			metadata.setBackground(result.getInt("background"));
			metadata.setHintContent(result.getString("hint_content"));
			metadata.setHintTitle(result.getString("hint_title"));
		}

		importProperties(view);

		if ( logger.isDebugEnabled() ) logger.debug("   imported version "+((IDBMetadata)view).getDBMetadata().getInitialVersion().getVersion()+" of "+((IDBMetadata)view).getDBMetadata().getDebugName());

		if ( mustImportViewContent ) {
			// 2 : we import the objects and create the corresponding elements if they do not exist yet
			//        importing an element will automatically import the relationships to and from this element
			prepareImportViewsObjects(((IIdentifier)view).getId(), ((IDBMetadata)view).getDBMetadata().getInitialVersion().getVersion());
			while ( importViewsObjects(model, view) ) {
				// each loop imports an object
			}

			// 3 : we import the connections and create the corresponding relationships if they do not exist yet
			prepareImportViewsConnections(((IIdentifier)view).getId(), ((IDBMetadata)view).getDBMetadata().getInitialVersion().getVersion());
			while ( importViewsConnections(model) ) {
				// each loop imports a connection
			}

			model.resolveSourceConnections();
			model.resolveTargetConnections();
		}

		if ( hasJustBeenCreated )
			model.countObject(view, false, null);

		return view;
	}

	/**
	 * This method imports a view object<br>
	 * if the corresponding element does not exists, it is imported
	 */
	public IDiagramModelObject importViewObjectFromId(DBArchimateModel model, String id, int version, boolean mustCreateCopy) throws Exception {
		if ( logger.isDebugEnabled() ) {
			if ( mustCreateCopy )
				logger.debug("Importing a copy of view object id "+id);
			else {
				logger.debug("Importing view object id "+id);
			}
		}

		// 1 : we create or update the view object
		EObject viewObject = null;
		String versionString = (version==0) ? "(SELECT MAX(version) FROM "+this.schema+"views_objects WHERE id = v.id)" : String.valueOf(version);

		try ( ResultSet resultViewObject = select("SELECT id, version, class, container_id, element_id, diagram_ref_id, border_color, border_type, content, documentation, hint_content, hint_title, is_locked, image_path, image_position, line_color, line_width, fill_color, font, font_color, name, notes, text_alignment, text_position, type, x, y, width, height, checksum, created_on FROM "+this.schema+"views_objects v WHERE id = ? AND version = "+versionString, id) ) {
			resultViewObject.next();
			
			DBMetadata metadata;

			viewObject = model.getAllViewObjects().get(id);
			if ( viewObject == null || mustCreateCopy ) {
				if ( resultViewObject.getString("class").startsWith("Canvas") )
					viewObject = DBCanvasFactory.eINSTANCE.create(resultViewObject.getString("class"));
				else
					viewObject = DBArchimateFactory.eINSTANCE.create(resultViewObject.getString("class"));

				((IIdentifier)viewObject).setId(mustCreateCopy ? model.getIDAdapter().getNewID() : id);
				
				metadata = ((IDBMetadata)viewObject).getDBMetadata();
				
				metadata.getInitialVersion().setVersion(1);
				metadata.getInitialVersion().setChecksum(resultViewObject.getString("checksum"));
				metadata.getInitialVersion().setTimestamp(resultViewObject.getTimestamp("created_on"));
				metadata.getCurrentVersion().setVersion(1);
				metadata.getCurrentVersion().setChecksum(resultViewObject.getString("checksum"));
				metadata.getCurrentVersion().setTimestamp(resultViewObject.getTimestamp("created_on"));
			} else {
				metadata = ((IDBMetadata)viewObject).getDBMetadata();
				
				metadata.getInitialVersion().setVersion(resultViewObject.getInt("version"));
				metadata.getInitialVersion().setChecksum(resultViewObject.getString("checksum"));
				metadata.getInitialVersion().setTimestamp(resultViewObject.getTimestamp("created_on"));
				metadata.getCurrentVersion().setVersion(resultViewObject.getInt("version"));
				metadata.getCurrentVersion().setChecksum(resultViewObject.getString("checksum"));
				metadata.getCurrentVersion().setTimestamp(resultViewObject.getTimestamp("created_on"));
			}
			
			metadata.getDatabaseVersion().setVersion(resultViewObject.getInt("version"));
			metadata.getDatabaseVersion().setChecksum(resultViewObject.getString("checksum"));
			metadata.getDatabaseVersion().setTimestamp(resultViewObject.getTimestamp("created_on"));
			metadata.getLatestDatabaseVersion().setVersion(resultViewObject.getInt("version"));
			metadata.getLatestDatabaseVersion().setChecksum(resultViewObject.getString("checksum"));
			metadata.getLatestDatabaseVersion().setTimestamp(resultViewObject.getTimestamp("created_on"));

			if ( viewObject instanceof IDiagramModelArchimateComponent && resultViewObject.getString("element_id") != null) {
				// we check that the element already exists. If not, we import it in shared mode
				IArchimateElement element = model.getAllElements().get(resultViewObject.getString("element_id"));
				if ( element == null )
					importElementFromId(model, null, resultViewObject.getString("element_id"), 0, false, true);
			}

			metadata.setArchimateConcept(model.getAllElements().get(resultViewObject.getString("element_id")));
			metadata.setReferencedModel(model.getAllViews().get(resultViewObject.getString("diagram_ref_id")));
			metadata.setType(resultViewObject.getInt("type"));
			metadata.setBorderColor(resultViewObject.getString("border_color"));
			metadata.setBorderType(resultViewObject.getInt("border_type"));
			metadata.setContent(resultViewObject.getString("content"));
			metadata.setDocumentation(resultViewObject.getString("documentation"));
			if ( resultViewObject.getObject("element_id") == null ) metadata.setName(resultViewObject.getString("name"));
			metadata.setHintContent(resultViewObject.getString("hint_content"));
			metadata.setHintTitle(resultViewObject.getString("hint_title"));
			metadata.setLocked(resultViewObject.getObject("is_locked"));
			metadata.setImagePath(resultViewObject.getString("image_path"));
			metadata.setImagePosition(resultViewObject.getInt("image_position"));
			metadata.setLineColor(resultViewObject.getString("line_color"));
			metadata.setLineWidth(resultViewObject.getInt("line_width"));
			metadata.setFillColor(resultViewObject.getString("fill_color"));
			metadata.setFont(resultViewObject.getString("font"));
			metadata.setFontColor(resultViewObject.getString("font_color"));
			metadata.setNotes(resultViewObject.getString("notes"));
			metadata.setTextAlignment(resultViewObject.getInt("text_alignment"));
			metadata.setTextPosition(resultViewObject.getInt("text_position"));
			metadata.setBounds(resultViewObject.getInt("x"), resultViewObject.getInt("y"), resultViewObject.getInt("width"), resultViewObject.getInt("height"));

			// we check if the view object must be changed from container
			if ( viewObject instanceof IDiagramModelObject ) {
				IDiagramModelContainer newContainer = model.getAllViews().get(resultViewObject.getString("container_id"));
				if ( newContainer == null )
					newContainer = (IDiagramModelContainer) model.getAllViewObjects().get(resultViewObject.getString("container_id"));
				IDiagramModelContainer currentContainer = (IDiagramModelContainer) ((IDiagramModelObject)viewObject).eContainer();		

				if ( currentContainer != null ) {
					if ( newContainer != currentContainer ) {
						if ( logger.isTraceEnabled() ) logger.trace("   Removing from container "+((IDBMetadata)currentContainer).getDBMetadata().getDebugName());
						currentContainer.getChildren().remove(viewObject);
					} else
						newContainer = null;		// no need to assign it again to the same container
				}
				
				if ( newContainer != null ) {
					if ( logger.isTraceEnabled() ) logger.trace("   Assigning to container "+((IDBMetadata)newContainer).getDBMetadata().getDebugName());
					newContainer.getChildren().add((IDiagramModelObject)viewObject);
				}
			}
			
			EObject viewContainer = viewObject.eContainer();
			while ( (viewContainer!= null) && !(viewContainer instanceof IDiagramModel) ) {
				viewContainer = viewContainer.eContainer();
			}
			if ( viewContainer!= null ) ((IDBMetadata)viewContainer).getDBMetadata().setChecksumValid(false);

			// If the object has got properties but does not have a linked element, then it may have distinct properties
			if ( viewObject instanceof IProperties && resultViewObject.getString("element_id")==null ) {
				importProperties((IProperties)viewObject);
			}

			model.countObject(viewObject, false, null);
			
			// if the object contains an image, we store its path to import it later
			if ( resultViewObject.getString("image_path") != null )
				this.allImagePaths.add(resultViewObject.getString("image_path"));

			if ( logger.isDebugEnabled() ) logger.debug("   imported version "+((IDBMetadata)viewObject).getDBMetadata().getInitialVersion().getVersion()+" of "+((IDBMetadata)viewObject).getDBMetadata().getDebugName());
		}

		return (IDiagramModelObject)viewObject;
	}

	/**
	 * This method imports a view Connection<br>
	 * if the corresponding relationship does not exists, it is imported
	 */
	public IDiagramModelConnection importViewConnectionFromId(DBArchimateModel model, String id, int version, boolean mustCreateCopy) throws Exception {
		if ( logger.isDebugEnabled() ) {
			if ( mustCreateCopy )
				logger.debug("Importing a copy of view connection id "+id);
			else {
				logger.debug("Importing view connection id "+id);
			}
		}

		// 1 : we create or update the view connection
		EObject viewConnection = null;
		String versionString = (version==0) ? "(SELECT MAX(version) FROM "+this.schema+"views_connections WHERE id = v.id)" : String.valueOf(version);

		try ( ResultSet resultViewConnection = select("SELECT id, version, class, container_id, name, documentation, is_locked, line_color, line_width, font, font_color, relationship_id, source_object_id, target_object_id, text_position, type, checksum, created_on FROM "+this.schema+"views_connections v WHERE id = ? AND version = "+versionString, id) ) {
			resultViewConnection.next();
			
			DBMetadata metadata;

			viewConnection= model.getAllViewConnections().get(id);
			if ( viewConnection == null || mustCreateCopy ) {
				if ( resultViewConnection.getString("class").startsWith("Canvas") )
					viewConnection = DBCanvasFactory.eINSTANCE.create(resultViewConnection.getString("class"));
				else
					viewConnection = DBArchimateFactory.eINSTANCE.create(resultViewConnection.getString("class"));

				((IIdentifier)viewConnection).setId(mustCreateCopy ? model.getIDAdapter().getNewID() : id);
			}

			if ( viewConnection instanceof IDiagramModelArchimateConnection && resultViewConnection.getString("relationship_id") != null) {
				// we check that the relationship already exists. If not, we import it (this may be the case during an individual view import.
				IArchimateRelationship relationship = model.getAllRelationships().get(resultViewConnection.getString("relationship_id"));
				if ( relationship == null ) {
					importRelationshipFromId(model, null, resultViewConnection.getString("relationship_id"), 0, false);
				}
			}
			
			metadata = ((IDBMetadata)viewConnection).getDBMetadata();

			metadata.setName(resultViewConnection.getString("name"));
			metadata.setLocked(resultViewConnection.getObject("is_locked"));
			metadata.setDocumentation(resultViewConnection.getString("documentation"));
			metadata.setLineColor(resultViewConnection.getString("line_color"));
			metadata.setLineWidth(resultViewConnection.getInt("line_width"));
			metadata.setFont(resultViewConnection.getString("font"));
			metadata.setFontColor(resultViewConnection.getString("font_color"));
			metadata.setType(resultViewConnection.getInt("type"));
			metadata.setTextPosition(resultViewConnection.getInt("text_position"));
			metadata.setType(resultViewConnection.getInt("type"));
			metadata.setArchimateConcept(model.getAllRelationships().get(resultViewConnection.getString("relationship_id")));

			metadata.getInitialVersion().setVersion(resultViewConnection.getInt("version"));
			metadata.getInitialVersion().setChecksum(resultViewConnection.getString("checksum"));
			metadata.getInitialVersion().setTimestamp(resultViewConnection.getTimestamp("created_on"));
			metadata.getCurrentVersion().setVersion(resultViewConnection.getInt("version"));
			metadata.getCurrentVersion().setChecksum(resultViewConnection.getString("checksum"));
			metadata.getCurrentVersion().setTimestamp(resultViewConnection.getTimestamp("created_on"));
			metadata.getDatabaseVersion().setVersion(resultViewConnection.getInt("version"));
			metadata.getDatabaseVersion().setChecksum(resultViewConnection.getString("checksum"));
			metadata.getDatabaseVersion().setTimestamp(resultViewConnection.getTimestamp("created_on"));
			metadata.getLatestDatabaseVersion().setVersion(resultViewConnection.getInt("version"));
			metadata.getLatestDatabaseVersion().setChecksum(resultViewConnection.getString("checksum"));
			metadata.getLatestDatabaseVersion().setTimestamp(resultViewConnection.getTimestamp("created_on"));

			if ( viewConnection instanceof IDiagramModelConnection ) {
                IConnectable source = model.getAllViewObjects().get(resultViewConnection.getString("source_object_id"));
                IConnectable target = model.getAllViewObjects().get(resultViewConnection.getString("target_object_id"));
                
                if ( source != null ) {
                    // source is an object and is reputed already imported, so we can set it right away
                    ((IDiagramModelConnection)viewConnection).setSource(source);
                    source.addConnection((IDiagramModelConnection)viewConnection);
                } else {
                    // source is another connection and may not be already loaded. So we register it for future resolution
                    model.registerSourceConnection((IDiagramModelConnection)viewConnection, this.currentResultSet.getString("source_object_id"));
                }
                
                if ( target != null ) {
                    // target is an object and is reputed already imported, so we can set it right away
                    ((IDiagramModelConnection)viewConnection).setTarget(target);
                    target.addConnection((IDiagramModelConnection)viewConnection);
                } else {
                    // target is another connection and may not be already loaded. So we register it for future resolution
                    model.registerTargetConnection((IDiagramModelConnection)viewConnection, this.currentResultSet.getString("target_object_id"));
                }
            }

			if ( viewConnection instanceof IDiagramModelConnection ) {
				((IDiagramModelConnection)viewConnection).getBendpoints().clear();
				try ( ResultSet resultBendpoints = select("SELECT start_x, start_y, end_x, end_y FROM "+this.schema+"bendpoints WHERE parent_id = ? AND parent_version = "+versionString+" ORDER BY rank", ((IIdentifier)viewConnection).getId()) ) {
					while(resultBendpoints.next()) {
						IDiagramModelBendpoint bendpoint = DBArchimateFactory.eINSTANCE.createDiagramModelBendpoint();
						bendpoint.setStartX(resultBendpoints.getInt("start_x"));
						bendpoint.setStartY(resultBendpoints.getInt("start_y"));
						bendpoint.setEndX(resultBendpoints.getInt("end_x"));
						bendpoint.setEndY(resultBendpoints.getInt("end_y"));
						((IDiagramModelConnection)viewConnection).getBendpoints().add(bendpoint);
					}
				}
			}

			// If the connection has got properties but does not have a linked relationship, then it may have distinct properties
			if ( viewConnection instanceof IProperties && resultViewConnection.getString("relationship_id")==null ) {
				importProperties((IProperties)viewConnection);
			}

			model.countObject(viewConnection, false, null);

			if ( logger.isDebugEnabled() ) logger.debug("   imported version "+((IDBMetadata)viewConnection).getDBMetadata().getInitialVersion().getVersion()+" of "+((IDBMetadata)viewConnection).getDBMetadata().getDebugName());

		}

		return (IDiagramModelConnection)viewConnection;
	}




	/**
	 * gets the latest model version in the database (0 if the model does not exist in the database)
	 */
	public int getLatestModelVersion(DBArchimateModel model) throws Exception {
		// we use COALESCE to guarantee that a value is returned, even if the model does not exist in the database
		try ( ResultSet result = select("SELECT COALESCE(MAX(version),0) as version FROM "+this.schema+"models WHERE id = ?", model.getId()) ) {
			result.next();
			return result.getInt("version");
		}
	}

	public void reset() throws SQLException {
		if ( this.currentResultSet != null ) {
			this.currentResultSet.close();
			this.currentResultSet = null;
		}

		// we reset the counters
		this.countElementsToImport = 0;
		this.countElementsImported = 0;
		this.countRelationshipsToImport = 0;
		this.countRelationshipsImported = 0;
		this.countFoldersToImport = 0;
		this.countFoldersImported = 0;
		this.countViewsToImport = 0;
		this.countViewsImported = 0;
		this.countViewObjectsToImport = 0;
		this.countViewObjectsImported = 0;
		this.countViewConnectionsToImport = 0;
		this.countViewConnectionsImported = 0;
		this.countImagesToImport = 0;
		this.countImagesImported = 0;

		// we empty the hashmap
		this.allImagePaths.clear();
	}

	@Override
	public void close() throws SQLException {
		reset();

		if ( !this.isExportConnectionDuplicate )
			super.close();
	}
	
	/**
	 * Check all the components in the database that have been move to a new folder and set them in the new folder<br>
	 * <br>
	 * This methods does nothing if the model is the latest in the database
	 * @param model
	 * @throws Exception
	 */
    public void setFolderToLastKnown(DBArchimateModel model) throws Exception {
        if ( model .isLatestVersionImported() )
            return;

        // elements
        try ( ResultSet result = select("SELECT m2.element_id AS element_id, m2.parent_folder_id AS parent_folder_id"
                + " FROM "+this.schema+"elements_in_model m1"
                + " JOIN "+this.schema+"elements_in_model m2 ON m1.element_id = m2.element_id AND m1.model_id = m2.model_id"
                + " WHERE m1.model_id = ? AND m1.model_version = ? AND m2.model_version = ? AND m1.parent_folder_id <> m2.parent_folder_id"
                , model.getId()
                , model.getInitialVersion().getVersion()
                , model.getDatabaseVersion().getVersion()
                ) ) {
            while (result.next() ) {
                IArchimateElement element = model.getAllElements().get(result.getString("element_id"));
                if ( element != null ) {
                    IFolder parentFolder = model.getAllFolders().get(result.getString("parent_folder_id"));
                    if ( parentFolder != null )
                        ((IDBMetadata)element).getDBMetadata().setParentFolder(parentFolder);
                }
            }
        }

        // relationships
        try ( ResultSet result = select("SELECT m2.relationship_id AS relationship_id, m2.parent_folder_id AS parent_folder_id"
                + " FROM "+this.schema+"relationships_in_model m1"
                + " JOIN "+this.schema+"relationships_in_model m2 ON m1.relationship_id = m2.relationship_id AND m1.model_id = m2.model_id"
                + " WHERE m1.model_id = ? AND m1.model_version = ? AND m2.model_version = ? AND m1.parent_folder_id <> m2.parent_folder_id"
                , model.getId()
                , model.getInitialVersion().getVersion()
                , model.getDatabaseVersion().getVersion()
                ) ) {
            while (result.next() ) {
                IArchimateRelationship relationship = model.getAllRelationships().get(result.getString("relationship_id"));
                if ( relationship != null ) {
                    IFolder parentFolder = model.getAllFolders().get(result.getString("parent_folder_id"));
                    if ( parentFolder != null )
                        ((IDBMetadata)relationship).getDBMetadata().setParentFolder(parentFolder);
                }
            }
        }
        
        // folders
        try ( ResultSet result = select("SELECT m2.folder_id AS folder_id, m2.parent_folder_id AS parent_folder_id"
                + " FROM "+this.schema+"folders_in_model m1"
                + " JOIN "+this.schema+"folders_in_model m2 ON m1.folder_id = m2.folder_id AND m1.model_id = m2.model_id"
                + " WHERE m1.model_id = ? AND m1.model_version = ? AND m2.model_version = ? AND m1.parent_folder_id <> m2.parent_folder_id"
                , model.getId()
                , model.getInitialVersion().getVersion()
                , model.getDatabaseVersion().getVersion()
                ) ) {
            while (result.next() ) {
                IFolder folder = model.getAllFolders().get(result.getString("view_id"));
                if ( folder != null ) {
                    IFolder parentFolder = model.getAllFolders().get(result.getString("parent_folder_id"));
                    if ( parentFolder != null )
                        ((IDBMetadata)folder).getDBMetadata().setParentFolder(parentFolder);
                }
            }
        }
        
        // views
        try ( ResultSet result = select("SELECT m2.view_id AS view_id, m2.parent_folder_id AS parent_folder_id"
                + " FROM "+this.schema+"views_in_model m1"
                + " JOIN "+this.schema+"views_in_model m2 ON m1.view_id = m2.view_id AND m1.model_id = m2.model_id"
                + " WHERE m1.model_id = ? AND m1.model_version = ? AND m2.model_version = ? AND m1.parent_folder_id <> m2.parent_folder_id"
                , model.getId()
                , model.getInitialVersion().getVersion()
                , model.getDatabaseVersion().getVersion()
                ) ) {
            while (result.next() ) {
                IDiagramModel view = model.getAllViews().get(result.getString("view_id"));
                if ( view != null ) {
                    IFolder parentFolder = model.getAllFolders().get(result.getString("parent_folder_id"));
                    if ( parentFolder != null )
                        ((IDBMetadata)view).getDBMetadata().setParentFolder(parentFolder);
                }
            }
        }
    }
    
    /**
     * Check if the component has already been part of the model once, and sets it to the folder it was in.<br>
     * <br>
     * if the component has never been part of the model, then is it set in the default folder for this component
     * @param model
     * @throws Exception
     */
    public void setFolderToLastKnown(DBArchimateModel model, IArchimateElement element) throws Exception {
        if ( !model.isLatestVersionImported() ) {
            IFolder parentFolder = null;
        	
            try ( ResultSet result = select("SELECT parent_folder_id, model_version"
                    + " FROM "+this.schema+"elements_in_model"
                    + " WHERE model_id = ? AND element_id = ? AND model_version = (SELECT MAX(model_version) FROM "+this.schema+"elements_in_model WHERE model_id = ? AND element_id = ?)"
                    , model.getId()
                    , element.getId()
                    , model.getId()
                    , element.getId()
                    ) ) {
                if ( result.next() )
                    parentFolder = model.getAllFolders().get(result.getString("parent_folder_id"));
            }
            
            if (parentFolder == null )
                parentFolder = model.getDefaultFolderForObject(element);
            
            ((IDBMetadata)element).getDBMetadata().setParentFolder(parentFolder);
        }
    }
    
    /**
     * Check if the component has already been part of the model once, and sets it to the folder it was in.<br>
     * <br>
     * if the component has never been part of the model, then is it set in the default folder for this component
     * @param model
     * @throws Exception
     */
    public void setFolderToLastKnown(DBArchimateModel model, IArchimateRelationship relationship) throws Exception {
        if ( !model.isLatestVersionImported() ) {
            IFolder parentFolder = null;

            try ( ResultSet result = select("SELECT parent_folder_id, model_version"
                    + " FROM "+this.schema+"relationships_in_model"
                    + " WHERE model_id = ? and relationship_id = ? AND model_version = (SELECT MAX(model_version) FROM "+this.schema+"relationships_in_model WHERE model_id = ? AND relationship_id = ?)"
                    , model.getId()
                    , relationship.getId()
                    , model.getId()
                    , relationship.getId()
                    ) ) {
                if ( result.next() )
                    parentFolder = model.getAllFolders().get(result.getString("parent_folder_id"));
            }
            
            if (parentFolder == null )
                parentFolder = model.getDefaultFolderForObject(relationship);
            
            ((IDBMetadata)relationship).getDBMetadata().setParentFolder(parentFolder);
        }
    }
    
    /**
     * Check if the component has already been part of the model once, and sets it to the folder it was in.<br>
     * <br>
     * if the component has never been part of the model, then is it set in the default folder for this component
     * @param model
     * @throws Exception
     */
    public void setFolderToLastKnown(DBArchimateModel model, IFolder folder) throws Exception {
        if ( !model.isLatestVersionImported() ) {
            IFolder parentFolder = null;
            
            try ( ResultSet result = select("SELECT parent_folder_id, model_version"
                    + " FROM "+this.schema+"folders_in_model"
                    + " WHERE model_id = ? and folder_id = ? AND model_version = (SELECT MAX(model_version) FROM "+this.schema+"folders_in_model WHERE model_id = ? AND folder_id = ?)"
                    , model.getId()
                    , folder.getId()
                    , model.getId()
                    , folder.getId()
                    ) ) {
                if ( result.next() )
                    parentFolder = model.getAllFolders().get(result.getString("parent_folder_id"));
            }
            if (parentFolder == null )
                parentFolder = model.getFolder(((IDBMetadata)folder).getDBMetadata().getFolderType());
            
            ((IDBMetadata)folder).getDBMetadata().setParentFolder(parentFolder);
        }
        

    }
    
    /**
     * Check if the component has already been part of the model once, and sets it to the folder it was in.<br>
     * <br>
     * if the component has never been part of the model, then is it set in the default folder for this component
     * @param model
     * @throws Exception
     */
    public void setFolderToLastKnown(DBArchimateModel model, IDiagramModel view) throws Exception {
        if ( !model.isLatestVersionImported() ) {
            IFolder parentFolder = null;

            try ( ResultSet result = select("SELECT parent_folder_id, model_version"
                    + " FROM "+this.schema+"views_in_model"
                    + " WHERE model_id = ? and view_id = ? AND model_version = (SELECT MAX(model_version) FROM "+this.schema+"views_in_model WHERE model_id = ? AND view_id = ?)"
                    , model.getId()
                    , view.getId()
                    , model.getId()
                    , view.getId()
                    ) ) {
                if ( result.next() )
                    parentFolder = model.getAllFolders().get(result.getString("parent_folder_id"));
            }
            
            if (parentFolder == null )
                parentFolder = model.getDefaultFolderForObject(view);
            
            ((IDBMetadata)view).getDBMetadata().setParentFolder(parentFolder);
        }
    }
}