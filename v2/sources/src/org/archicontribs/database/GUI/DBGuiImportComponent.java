/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */

package org.archicontribs.database.GUI;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Level;
import org.archicontribs.database.DBLogger;
import org.archicontribs.database.DBPlugin;
import org.archicontribs.database.connection.DBDatabaseImportConnection;

import com.archimatetool.model.IDiagramModel;
import org.archicontribs.database.model.DBArchimateModel;
import org.archicontribs.database.model.DBArchimateFactory;
import org.archicontribs.database.model.DBCanvasFactory;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import com.archimatetool.editor.ui.services.ViewManager;
import com.archimatetool.editor.views.tree.ITreeModelView;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IFolder;

public class DBGuiImportComponent extends DBGui {
	@SuppressWarnings("hiding")
	protected static final DBLogger logger = new DBLogger(DBGuiImportComponent.class);

	protected DBArchimateModel importedModel;
	protected IArchimateDiagramModel selectedView;
	protected IFolder selectedFolder;

	private Group grpFilter;
	private Group grpComponent;

	Composite compoElements;
	private Button radioOptionElement;
	private Button radioOptionView;
	private Text filterName;
	private Button ignoreCase;
	private Button hideOption;             // to hide empty names for elements and relationships, top level folders and default views
	private Button hideAlreadyInModel;
	
	DBDatabaseImportConnection importConnection;

	//private Composite compoContainers;

	//private Composite compoFolders;
	//private Button strategyFolders;
	//private Button applicationFolders;
	//private Button businessFolders;
	//private Button technologyFolders;
	//private Button otherFolders;
	//private Button motivationFolders;
	//private Button implementationFolders;

	Composite compoViews;
	private Button archimateViews;
	private Button canvasViews;
	private Button sketchViews;

	Label lblComponents;
	Table tblComponents;


	ComponentLabel resourceLabel;
	ComponentLabel capabilityLabel;
	ComponentLabel courseOfActionLabel;
	ComponentLabel applicationComponentLabel;
	ComponentLabel applicationCollaborationLabel;
	ComponentLabel applicationInterfaceLabel;
	ComponentLabel applicationFunctionLabel;
	ComponentLabel applicationInteractionLabel;
	ComponentLabel applicationEventLabel;
	ComponentLabel applicationServiceLabel;
	ComponentLabel dataObjectLabel;
	ComponentLabel applicationProcessLabel;
	ComponentLabel businessActorLabel;
	ComponentLabel businessRoleLabel;
	ComponentLabel businessCollaborationLabel;
	ComponentLabel businessInterfaceLabel;
	ComponentLabel businessProcessLabel;
	ComponentLabel businessFunctionLabel;
	ComponentLabel businessInteractionLabel;
	ComponentLabel businessEventLabel;
	ComponentLabel businessServiceLabel;
	ComponentLabel businessObjectLabel;
	ComponentLabel contractLabel;
	ComponentLabel representationLabel;
	ComponentLabel nodeLabel;
	ComponentLabel deviceLabel;
	ComponentLabel systemSoftwareLabel;
	ComponentLabel technologyCollaborationLabel;
	ComponentLabel technologyInterfaceLabel;
	ComponentLabel pathLabel;
	ComponentLabel communicationNetworkLabel;
	ComponentLabel technologyFunctionLabel;
	ComponentLabel technologyProcessLabel;
	ComponentLabel technologyInteractionLabel;
	ComponentLabel technologyEventLabel;
	ComponentLabel technologyServiceLabel;
	ComponentLabel artifactLabel;
	ComponentLabel equipmentLabel;
	ComponentLabel facilityLabel;
	ComponentLabel distributionNetworkLabel;
	ComponentLabel materialLabel;
	ComponentLabel workpackageLabel;
	ComponentLabel deliverableLabel;
	ComponentLabel implementationEventLabel;
	ComponentLabel plateauLabel;
	ComponentLabel gapLabel;
	ComponentLabel stakeholderLabel;
	ComponentLabel driverLabel;
	ComponentLabel assessmentLabel;
	ComponentLabel goalLabel;
	ComponentLabel outcomeLabel;
	ComponentLabel principleLabel;
	ComponentLabel requirementLabel;
	ComponentLabel constaintLabel;
	ComponentLabel smeaningLabel;
	ComponentLabel valueLabel;
	ComponentLabel productLabel;
	ComponentLabel locationLabel;

	ComponentLabel[] allElementLabels;

	/**
	 * Creates the GUI to import components
	 * @throws Exception 
	 */
	public DBGuiImportComponent(DBArchimateModel model, IArchimateDiagramModel view, IFolder folder, String title) throws Exception {
		super(title);

		this.includeNeo4j = false;

		setMessage("Counting model's components");
		model.countAllObjects();
		if ( logger.isDebugEnabled() ) logger.debug("The model has got "+model.getAllElements().size()+" elements and "+model.getAllRelationships().size()+" relationships.");
		closeMessage();		
		
		if ( logger.isDebugEnabled() ) logger.debug("Setting up GUI for importing a component (plugin version "+DBPlugin.pluginVersion+").");

		// model in which the component should be imported
		this.importedModel = model;

		// if specified, the imported element or relationship will be instantiated as an object or a connection in the view
		this.selectedView = view;

		// if specified, the imported view will be instantiated into the folder (if the root folder type is view)
		this.selectedFolder = folder;

		createAction(ACTION.One, "Choose component");
		setActiveAction(ACTION.One);

		// we show the option in the bottom
		setOption("Import type :", "Shared", "The component will be shared between models. All your modifications will be visible by other models.", "Copy", "A copy of the component will be created. All your modifications will remain private to your model and will not be visible by other models.", DBPlugin.INSTANCE.getPreferenceStore().getBoolean("importShared"));
		
		// We activate the btnDoAction button : if the user select the "Import" button --> call the doImport() method
		setBtnAction("Import", new SelectionListener() {
			@Override
            public void widgetSelected(SelectionEvent event) {
				DBGuiImportComponent.this.btnDoAction.setEnabled(false);
				try {
					doImport();
				} catch (Exception err) {
					DBGui.popup(Level.ERROR, "An exception has been raised during import.", err);
				}
			}
			@Override
            public void widgetDefaultSelected(SelectionEvent event) { widgetSelected(event); }
		});

		// We activate the Eclipse Help framework
		setHelpHref("importComponent.html");

		getDatabases(false);
	}

	/**
	 * Called when a database is selected in the comboDatabases and that the connection to this database succeeded.<br>
	 */
	@Override
	protected void connectedToDatabase(boolean ignore) {
	    this.importConnection = new DBDatabaseImportConnection(getDatabaseConnection());
	    
		enableOption();
		createGrpFilter();
		createGrpComponents();
		this.compoRightBottom.setVisible(true);
		this.compoRightBottom.layout();
	}

	private void createGrpFilter() {
		this.grpFilter = new Group(this.compoRightBottom, SWT.NONE);
		this.grpFilter.setBackground(GROUP_BACKGROUND_COLOR);
		this.grpFilter.setFont(GROUP_TITLE_FONT);
		this.grpFilter.setText("Filter : ");
		FormData fd = new FormData();
		fd.top = new FormAttachment(0);
		fd.left = new FormAttachment(0);
		fd.right = new FormAttachment(100);
		fd.bottom = new FormAttachment(50, -5);
		this.grpFilter.setLayoutData(fd);
		this.grpFilter.setLayout(new FormLayout());

		Label chooseCategory = new Label(this.grpFilter, SWT.NONE);
		chooseCategory.setBackground(GROUP_BACKGROUND_COLOR);
		chooseCategory.setFont(BOLD_FONT);
		chooseCategory.setText("Category :");
		fd = new FormData();
		fd.top = new FormAttachment(0, 20);
		fd.left = new FormAttachment(0, 10);
		chooseCategory.setLayoutData(fd);

		this.radioOptionElement = new Button(this.grpFilter, SWT.RADIO);
		this.radioOptionElement.setBackground(GROUP_BACKGROUND_COLOR);
		this.radioOptionElement.setText("Elements");
		this.radioOptionElement.setSelection(true);
		this.radioOptionElement.addSelectionListener(new SelectionListener() {
			@Override public void widgetSelected(SelectionEvent event) {
				try {
					getElements();
				} catch (Exception err) {
					DBGui.popup(Level.ERROR, "An exception has been raised during the import.", err);
				}
			} @Override public void widgetDefaultSelected(SelectionEvent event) { widgetSelected(event); }
		});
		fd = new FormData();
		fd.top = new FormAttachment(chooseCategory, 5);
		fd.left = new FormAttachment(0, 20);
		this.radioOptionElement.setLayoutData(fd);

		this.radioOptionView = new Button(this.grpFilter, SWT.RADIO);
		this.radioOptionView.setBackground(GROUP_BACKGROUND_COLOR);
		this.radioOptionView.setText("Views");
		this.radioOptionView.addSelectionListener(new SelectionListener() {
			@Override public void widgetSelected(SelectionEvent event) {
				try {
					getViews();
				} catch (Exception err) {
					DBGui.popup(Level.ERROR, "An exception has been raised during the import.", err);
				}
			}
			@Override public void widgetDefaultSelected(SelectionEvent event) { widgetSelected(event); }
		});
		fd = new FormData();
		fd.top = new FormAttachment(this.radioOptionElement, 5);
		fd.left = new FormAttachment(0, 20);
		this.radioOptionView.setLayoutData(fd);

		Label chooseName = new Label(this.grpFilter, SWT.NONE);
		chooseName.setBackground(GROUP_BACKGROUND_COLOR);
		chooseName.setFont(BOLD_FONT);
		chooseName.setText("Name filter :");
		fd = new FormData();
		fd.top = new FormAttachment(this.radioOptionView, 10);
		fd.left = new FormAttachment(0, 10);
		chooseName.setLayoutData(fd);

		this.filterName = new Text(this.grpFilter, SWT.NONE);
		fd = new FormData();
		fd.top = new FormAttachment(chooseName, 5);
		fd.left = new FormAttachment(0, 10);
		fd.right = new FormAttachment(0, 125);
		this.filterName.setLayoutData(fd);
		this.filterName.addModifyListener(new ModifyListener() {
			@Override
            public void modifyText(ModifyEvent event) {
				try {
					if ( DBGuiImportComponent.this.importConnection.isConnected() ) {
						if ( DBGuiImportComponent.this.compoElements.isVisible() )
							getElements();
						//else if ( compoContainers.isVisible() )
						//	getContainers();
						//else if ( compoFolders.isVisible() )
						//	getFolders();
						else if ( DBGuiImportComponent.this.compoViews.isVisible() )
							getViews();
					}
				} catch (Exception err) {
					DBGui.popup(Level.ERROR, "An exception has been raised during the import.", err);
				} 
			}
		});

		this.ignoreCase = new Button(this.grpFilter, SWT.CHECK);
		this.ignoreCase.setBackground(GROUP_BACKGROUND_COLOR);
		this.ignoreCase.setText("Ignore case");
		this.ignoreCase.setSelection(true);
		fd = new FormData();
		fd.top = new FormAttachment(this.filterName, 5);
		fd.left = new FormAttachment(0, 10);
		this.ignoreCase.setLayoutData(fd);
		this.ignoreCase.addListener(SWT.MouseUp, new Listener() {
			@Override
			public void handleEvent(Event event) {
				try {
					if ( DBGuiImportComponent.this.importConnection.isConnected() ) {
						if ( DBGuiImportComponent.this.compoElements.isVisible() )
							getElements();
						//else if ( compoContainers.isVisible() )
						//	getContainers();
						//else if ( compoFolders.isVisible() )
						//	getFolders();
						else if ( DBGuiImportComponent.this.compoViews.isVisible() )
							getViews();
					}
				} catch (Exception err) {
					DBGui.popup(Level.ERROR, "An exception has been raised during the import.", err);
				} 
			}
		});

		createCompoElements();		this.compoElements.setVisible(true);
		//createCompoComposites();	compoContainers.setVisible(false);
		//createCompoFolders();		compoFolders.setVisible(false);
		createCompoViews();			this.compoViews.setVisible(false);
	}

	private void createCompoElements() {		
		this.compoElements = new Composite(this.grpFilter, SWT.NONE);
		this.compoElements.setBackground(GROUP_BACKGROUND_COLOR);
		FormData fd = new FormData();
		fd.top = new FormAttachment(0);
		fd.left = new FormAttachment(0, 135);
		fd.right = new FormAttachment(100, -10);
		fd.bottom = new FormAttachment(100, -10);
		this.compoElements.setLayoutData(fd);
		this.compoElements.setLayout(new FormLayout());

		Composite strategyActiveCompo = new Composite(this.compoElements, SWT.TRANSPARENT);
		Composite strategyBehaviorCompo = new Composite(this.compoElements, SWT.TRANSPARENT);
		Composite strategyPassiveCompo = new Composite(this.compoElements, SWT.TRANSPARENT );

		Composite businessActiveCompo = new Composite(this.compoElements, SWT.TRANSPARENT);
		Composite businessBehaviorCompo = new Composite(this.compoElements, SWT.TRANSPARENT);
		Composite businessPassiveCompo = new Composite(this.compoElements, SWT.TRANSPARENT );

		Composite applicationActiveCompo = new Composite(this.compoElements, SWT.TRANSPARENT);
		Composite applicationBehaviorCompo = new Composite(this.compoElements, SWT.TRANSPARENT);
		Composite applicationPassiveCompo = new Composite(this.compoElements, SWT.TRANSPARENT);

		Composite technologyActiveCompo = new Composite(this.compoElements, SWT.TRANSPARENT);
		Composite technologyBehaviorCompo = new Composite(this.compoElements, SWT.TRANSPARENT);
		Composite technologyPassiveCompo = new Composite(this.compoElements, SWT.TRANSPARENT);

		Composite physicalActiveCompo = new Composite(this.compoElements, SWT.TRANSPARENT);
		Composite physicalBehaviorCompo = new Composite(this.compoElements, SWT.TRANSPARENT);
		Composite physicalPassive = new Composite(this.compoElements, SWT.TRANSPARENT);

		Composite implementationCompo = new Composite(this.compoElements, SWT.TRANSPARENT);


		Composite motivationCompo = new Composite(this.compoElements, SWT.TRANSPARENT);

		Composite otherCompo = new Composite(this.compoElements, SWT.TRANSPARENT);

		///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		// Strategy layer
		// Passive
		// Behavior
		this.capabilityLabel = new ComponentLabel(strategyBehaviorCompo,  "Capability");
		this.courseOfActionLabel = new ComponentLabel(strategyBehaviorCompo,  "Course Of Action");
		// Active
		this.resourceLabel = new ComponentLabel(strategyActiveCompo, "Resource");

		// Business layer
		// Passive
		this.productLabel = new ComponentLabel(businessPassiveCompo, "Product");
		// Behavior
		this.businessProcessLabel = new ComponentLabel(businessBehaviorCompo, "Business Process");
		this.businessFunctionLabel = new ComponentLabel(businessBehaviorCompo, "Business Function");
		this.businessInteractionLabel = new ComponentLabel(businessBehaviorCompo, "Business Interaction");
		this.businessEventLabel = new ComponentLabel(businessBehaviorCompo, "Business Event");
		this.businessServiceLabel = new ComponentLabel(businessBehaviorCompo, "Business Service");
		this.businessObjectLabel = new ComponentLabel(businessBehaviorCompo, "Business Object");
		this.contractLabel = new ComponentLabel(businessBehaviorCompo, "Contract");
		this.representationLabel = new ComponentLabel(businessBehaviorCompo, "Representation");
		// Active
		this.businessActorLabel = new ComponentLabel(businessActiveCompo, "Business Actor");
		this.businessRoleLabel = new ComponentLabel(businessActiveCompo, "Business Role");
		this.businessCollaborationLabel = new ComponentLabel(businessActiveCompo, "Business Collaboration");
		this.businessInterfaceLabel = new ComponentLabel(businessActiveCompo, "Business Interface");

		// Application layer
		//Passive
		this.dataObjectLabel = new ComponentLabel(applicationPassiveCompo, "Data Object");
		//Behavior
		this.applicationFunctionLabel = new ComponentLabel(applicationBehaviorCompo, "Application Function");
		this.applicationInteractionLabel = new ComponentLabel(applicationBehaviorCompo, "Application Interaction");
		this.applicationEventLabel = new ComponentLabel(applicationBehaviorCompo, "Application Event");
		this.applicationServiceLabel = new ComponentLabel(applicationBehaviorCompo, "Application Service");
		this.applicationProcessLabel = new ComponentLabel(applicationBehaviorCompo, "Application Process");
		//	Active		
		this.applicationComponentLabel = new ComponentLabel(applicationActiveCompo, "Application Component");
		this.applicationCollaborationLabel = new ComponentLabel(applicationActiveCompo, "Application Collaboration");
		this.applicationInterfaceLabel = new ComponentLabel(applicationActiveCompo, "Application Interface");

		// Technology layer
		// Passive
		this.artifactLabel = new ComponentLabel(technologyPassiveCompo, "Artifact");
		// Behavior
		this.technologyFunctionLabel = new ComponentLabel(technologyBehaviorCompo, "Technology Function");
		this.technologyProcessLabel = new ComponentLabel(technologyBehaviorCompo, "Technology Process");
		this.technologyInteractionLabel = new ComponentLabel(technologyBehaviorCompo, "Technology Interaction");
		this.technologyEventLabel = new ComponentLabel(technologyBehaviorCompo, "Technology Event");
		this.technologyServiceLabel = new ComponentLabel(technologyBehaviorCompo, "Technology Service");
		// Active
		this.nodeLabel = new ComponentLabel(technologyActiveCompo, "Node");
		this.deviceLabel = new ComponentLabel(technologyActiveCompo, "Device");
		this.systemSoftwareLabel = new ComponentLabel(technologyActiveCompo, "System Software");
		this.technologyCollaborationLabel = new ComponentLabel(technologyActiveCompo, "Technology Collaboration");
		this.technologyInterfaceLabel = new ComponentLabel(technologyActiveCompo, "Technology Interface");
		this.pathLabel = new ComponentLabel(technologyActiveCompo, "Path");
		this.communicationNetworkLabel = new ComponentLabel(technologyActiveCompo, "Communication Network");

		// Physical layer
		// Passive
		// Behavior
		this.materialLabel = new ComponentLabel(physicalBehaviorCompo, "Material");
		// Active
		this.equipmentLabel = new ComponentLabel(physicalActiveCompo, "Equipment");
		this.facilityLabel = new ComponentLabel(physicalActiveCompo, "Facility");
		this.distributionNetworkLabel = new ComponentLabel(physicalActiveCompo, "Distribution Network");

		// Implementation layer
		this.workpackageLabel = new ComponentLabel(implementationCompo, "Work Package");
		this.deliverableLabel = new ComponentLabel(implementationCompo, "Deliverable");
		this.implementationEventLabel = new ComponentLabel(implementationCompo, "Implementation Event");
		this.plateauLabel = new ComponentLabel(implementationCompo, "Plateau");
		this.gapLabel = new ComponentLabel(implementationCompo, "Gap");

		// Motivation layer
		this.stakeholderLabel = new ComponentLabel(motivationCompo, "Stakeholder");
		this.driverLabel = new ComponentLabel(motivationCompo, "Driver");
		this.assessmentLabel = new ComponentLabel(motivationCompo, "Assessment");
		this.goalLabel = new ComponentLabel(motivationCompo, "Goal");
		this.outcomeLabel = new ComponentLabel(motivationCompo, "Outcome");
		this.principleLabel = new ComponentLabel(motivationCompo, "Principle");
		this.requirementLabel = new ComponentLabel(motivationCompo, "Requirement");
		this.constaintLabel = new ComponentLabel(motivationCompo, "Constraint");
		this.smeaningLabel = new ComponentLabel(motivationCompo, "Meaning");
		this.valueLabel = new ComponentLabel(motivationCompo, "Value");

		// Containers !!!
		//
		//createTableItem(tblClasses, "Grouping");
		this.locationLabel = new ComponentLabel(otherCompo, "Location");

		this.allElementLabels = new ComponentLabel[]{ this.resourceLabel, this.capabilityLabel, this.courseOfActionLabel, this.applicationComponentLabel, this.applicationCollaborationLabel, this.applicationInterfaceLabel, this.applicationFunctionLabel, this.applicationInteractionLabel, this.applicationEventLabel, this.applicationServiceLabel, this.dataObjectLabel, this.applicationProcessLabel, this.businessActorLabel, this.businessRoleLabel, this.businessCollaborationLabel, this.businessInterfaceLabel, this.businessProcessLabel, this.businessFunctionLabel, this.businessInteractionLabel, this.businessEventLabel, this.businessServiceLabel, this.businessObjectLabel, this.contractLabel, this.representationLabel, this.nodeLabel, this.deviceLabel, this.systemSoftwareLabel, this.technologyCollaborationLabel, this.technologyInterfaceLabel, this.pathLabel, this.communicationNetworkLabel, this.technologyFunctionLabel, this.technologyProcessLabel, this.technologyInteractionLabel, this.technologyEventLabel, this.technologyServiceLabel, this.artifactLabel, this.equipmentLabel, this.facilityLabel, this.distributionNetworkLabel, this.materialLabel, this.workpackageLabel, this.deliverableLabel, this.implementationEventLabel, this.plateauLabel, this.gapLabel, this.stakeholderLabel, this.driverLabel, this.assessmentLabel, this.goalLabel, this.outcomeLabel, this.principleLabel, this.requirementLabel, this.constaintLabel, this.smeaningLabel, this.valueLabel, this.productLabel, this.locationLabel};

		Label passiveLabel = new Label(this.compoElements, SWT.TRANSPARENT | SWT.CENTER);
		Canvas passiveCanvas = new Canvas(this.compoElements, SWT.TRANSPARENT | SWT.BORDER);
		fd = new FormData();
		fd.top = new FormAttachment(0);
		fd.bottom = new FormAttachment(implementationCompo, 1, SWT.TOP);
		fd.left = new FormAttachment(0, 70);
		fd.right = new FormAttachment(0, 110);
		passiveCanvas.setLayoutData(fd);
		fd = new FormData();
		fd.top = new FormAttachment(passiveCanvas, 1, SWT.TOP);
		fd.left = new FormAttachment(0, 71);
		fd.right = new FormAttachment(0, 109);
		passiveLabel.setLayoutData(fd);
		passiveLabel.setText("Passive");
		passiveLabel.setBackground(PASSIVE_COLOR);

		Label behaviorLabel = new Label(this.compoElements, SWT.TRANSPARENT | SWT.CENTER);
		Canvas behaviorCanvas = new Canvas(this.compoElements, SWT.TRANSPARENT | SWT.BORDER);
		fd = new FormData();
		fd.top = new FormAttachment(0);
		fd.bottom = new FormAttachment(implementationCompo, 1, SWT.TOP);
		fd.left = new FormAttachment(0, 115);
		fd.right = new FormAttachment(55);
		behaviorCanvas.setLayoutData(fd);
		fd = new FormData();
		fd.top = new FormAttachment(behaviorCanvas, 1, SWT.TOP);
		fd.left = new FormAttachment(0, 116);
		fd.right = new FormAttachment(55, -1);
		behaviorLabel.setLayoutData(fd);
		behaviorLabel.setText("Behavior");
		behaviorLabel.setBackground(PASSIVE_COLOR);

		Label activeLabel = new Label(this.compoElements, SWT.TRANSPARENT | SWT.CENTER);
		Canvas activeCanvas = new Canvas(this.compoElements, SWT.TRANSPARENT | SWT.BORDER);
		fd = new FormData();
		fd.top = new FormAttachment(0);
		fd.bottom = new FormAttachment(implementationCompo, 1, SWT.TOP);
		fd.left = new FormAttachment(55, 5);
		fd.right = new FormAttachment(100, -65);
		activeCanvas.setLayoutData(fd);
		fd = new FormData();
		fd.top = new FormAttachment(activeCanvas, 1, SWT.TOP);
		fd.left = new FormAttachment(55, 6);
		fd.right = new FormAttachment(100, -66);
		activeLabel.setLayoutData(fd);
		activeLabel.setText("Active");
		activeLabel.setBackground(PASSIVE_COLOR);

		Label motivationLabel = new Label(this.compoElements, SWT.TRANSPARENT | SWT.CENTER);
		Canvas motivationCanvas = new Canvas(this.compoElements, SWT.TRANSPARENT);
		fd = new FormData();
		fd.top = new FormAttachment(0);
		fd.bottom = new FormAttachment(85, -2);
		fd.left = new FormAttachment(100, -60);
		fd.right = new FormAttachment(100);
		motivationCanvas.setLayoutData(fd);
		fd = new FormData();
		fd.top = new FormAttachment(motivationCanvas, 1, SWT.TOP);
		fd.left = new FormAttachment(100, -59);
		fd.right = new FormAttachment(100, -1);
		motivationLabel.setLayoutData(fd);
		motivationLabel.setText("Motivation");
		motivationLabel.setBackground(MOTIVATION_COLOR);

		PaintListener redraw = new PaintListener() {
			@Override
            public void paintControl(PaintEvent event) {
				event.gc.setAlpha(100);
				if ( event.widget == motivationCanvas ) 
					event.gc.setBackground(MOTIVATION_COLOR);
				else
					event.gc.setBackground(PASSIVE_COLOR);
				event.gc.fillRectangle(event.x, event.y, event.width, event.height);
			}
		};

		passiveCanvas.addPaintListener(redraw);
		behaviorCanvas.addPaintListener(redraw);
		activeCanvas.addPaintListener(redraw);
		motivationCanvas.addPaintListener(redraw);







		Label strategyLabel = new Label(this.compoElements, SWT.NONE);
		Canvas strategyCanvas = new Canvas(this.compoElements, SWT.NONE);
		fd = new FormData();
		fd.top = new FormAttachment(10, 2);
		fd.bottom = new FormAttachment(25, -2);
		fd.left = new FormAttachment(0);
		fd.right = new FormAttachment(100, -60);
		strategyCanvas.setLayoutData(fd);
		strategyCanvas.setBackground(STRATEGY_COLOR);
		fd = new FormData();
		fd.top = new FormAttachment(strategyCanvas, 0, SWT.CENTER);
		fd.left = new FormAttachment(strategyCanvas, 2, SWT.LEFT);
		strategyLabel.setLayoutData(fd);
		strategyLabel.setBackground(STRATEGY_COLOR);
		strategyLabel.setText("Strategy");

		Label businessLabel = new Label(this.compoElements, SWT.NONE);
		Canvas businessCanvas = new Canvas(this.compoElements, SWT.NONE);
		fd = new FormData();
		fd.top = new FormAttachment(25, 2);
		fd.bottom = new FormAttachment(40, -2);
		fd.left = new FormAttachment(0);
		fd.right = new FormAttachment(100, -60);
		businessCanvas.setLayoutData(fd);
		businessCanvas.setBackground(BUSINESS_COLOR);
		fd = new FormData();
		fd.top = new FormAttachment(businessCanvas, 0, SWT.CENTER);
		fd.left = new FormAttachment(businessCanvas, 2, SWT.LEFT);
		businessLabel.setLayoutData(fd);
		businessLabel.setBackground(BUSINESS_COLOR);
		businessLabel.setText("Business");

		Label applicationLabel = new Label(this.compoElements, SWT.NONE);
		Canvas applicationCanvas = new Canvas(this.compoElements, SWT.NONE);
		fd = new FormData();
		fd.top = new FormAttachment(40, 2);
		fd.bottom = new FormAttachment(55, -2);
		fd.left = new FormAttachment(0);
		fd.right = new FormAttachment(100, -60);
		applicationCanvas.setLayoutData(fd);
		applicationCanvas.setBackground(APPLICATION_COLOR);
		fd = new FormData();
		fd.top = new FormAttachment(applicationCanvas, 0, SWT.CENTER);
		fd.left = new FormAttachment(applicationCanvas, 2, SWT.LEFT);
		applicationLabel.setLayoutData(fd);
		applicationLabel.setBackground(APPLICATION_COLOR);
		applicationLabel.setText("Application");

		Label technologyLabel = new Label(this.compoElements, SWT.NONE);
		Canvas technologyCanvas = new Canvas(this.compoElements, SWT.NONE);
		fd = new FormData();
		fd.top = new FormAttachment(55, 2);
		fd.bottom = new FormAttachment(70, -2);
		fd.left = new FormAttachment(0);
		fd.right = new FormAttachment(100, -60);
		technologyCanvas.setLayoutData(fd);
		technologyCanvas.setBackground(TECHNOLOGY_COLOR);
		fd = new FormData();
		fd.top = new FormAttachment(technologyCanvas, 0, SWT.CENTER);
		fd.left = new FormAttachment(technologyCanvas, 2, SWT.LEFT);
		technologyLabel.setLayoutData(fd);
		technologyLabel.setBackground(TECHNOLOGY_COLOR);
		technologyLabel.setText("Technology");

		Label physicalLabel = new Label(this.compoElements, SWT.NONE);
		Canvas physicalCanvas = new Canvas(this.compoElements, SWT.NONE);
		fd = new FormData();
		fd.top = new FormAttachment(70, 2);
		fd.bottom = new FormAttachment(85, -2);
		fd.left = new FormAttachment(0);
		fd.right = new FormAttachment(100, -60);
		physicalCanvas.setLayoutData(fd);
		physicalCanvas.setBackground(PHYSICAL_COLOR);
		fd = new FormData();
		fd.top = new FormAttachment(physicalCanvas, 0, SWT.CENTER);
		fd.left = new FormAttachment(physicalCanvas, 2, SWT.LEFT);
		physicalLabel.setLayoutData(fd);
		physicalLabel.setBackground(PHYSICAL_COLOR);
		physicalLabel.setText("Physical");

		Label implementationLabel = new Label(this.compoElements, SWT.NONE);
		Canvas implementationCanvas = new Canvas(this.compoElements, SWT.NONE);
		fd = new FormData();
		fd.top = new FormAttachment(85, 2);
		fd.bottom = new FormAttachment(100);
		fd.left = new FormAttachment(0);
		fd.right = new FormAttachment(100, -65);
		implementationCanvas.setLayoutData(fd);
		implementationCanvas.setBackground(IMPLEMENTATION_COLOR);
		fd = new FormData();
		fd.top = new FormAttachment(implementationCanvas, 0, SWT.CENTER);
		fd.left = new FormAttachment(implementationCanvas, 2, SWT.LEFT);
		implementationLabel.setLayoutData(fd);
		implementationLabel.setBackground(IMPLEMENTATION_COLOR);
		implementationLabel.setText("Implementation");

		Canvas otherCanvas = new Canvas(this.compoElements, SWT.NONE);
		fd = new FormData();
		fd.top = new FormAttachment(85, 2);
		fd.bottom = new FormAttachment(100);
		fd.left = new FormAttachment(100, -60);
		fd.right = new FormAttachment(100);
		otherCanvas.setLayoutData(fd);

		// strategy + active
		fd = new FormData();
		fd.top = new FormAttachment(strategyCanvas, 0, SWT.TOP);
		fd.bottom = new FormAttachment(strategyCanvas, 0, SWT.BOTTOM);
		fd.left = new FormAttachment(activeCanvas, 0, SWT.LEFT);
		fd.right = new FormAttachment(activeCanvas, 0, SWT.RIGHT);
		strategyActiveCompo.setLayoutData(fd);
		RowLayout rd = new RowLayout(SWT.HORIZONTAL);
		rd.center = true;
		rd.fill = true;
		rd.justify = true;
		rd.wrap = true;
		rd.marginBottom = 5;
		rd.marginTop = 5;
		rd.marginLeft = 5;
		rd.marginRight = 5;
		rd.spacing = 0;
		strategyActiveCompo.setLayout(rd);

		// strategy + behavior
		fd = new FormData();
		fd.top = new FormAttachment(strategyCanvas, 0, SWT.TOP);
		fd.bottom = new FormAttachment(strategyCanvas, 0, SWT.BOTTOM);
		fd.left = new FormAttachment(behaviorCanvas, 0, SWT.LEFT);
		fd.right = new FormAttachment(behaviorCanvas, 0, SWT.RIGHT);
		strategyBehaviorCompo.setLayoutData(fd);
		strategyBehaviorCompo.setLayout(rd);

		// strategy + passive
		fd = new FormData();
		fd.top = new FormAttachment(strategyCanvas, 0, SWT.TOP);
		fd.bottom = new FormAttachment(strategyCanvas, 0, SWT.BOTTOM);
		fd.left = new FormAttachment(passiveCanvas, 0, SWT.LEFT);
		fd.right = new FormAttachment(passiveCanvas, 0, SWT.RIGHT);
		strategyPassiveCompo.setLayoutData(fd);
		strategyPassiveCompo.setLayout(rd);	

		// business + active
		fd = new FormData();
		fd.top = new FormAttachment(businessCanvas, 0, SWT.TOP);
		fd.bottom = new FormAttachment(businessCanvas, 0, SWT.BOTTOM);
		fd.left = new FormAttachment(activeCanvas, 0, SWT.LEFT);
		fd.right = new FormAttachment(activeCanvas, 0, SWT.RIGHT);
		businessActiveCompo.setLayoutData(fd);
		businessActiveCompo.setLayout(rd);

		// business + behavior
		fd = new FormData();
		fd.top = new FormAttachment(businessCanvas, 0, SWT.TOP);
		fd.bottom = new FormAttachment(businessCanvas, 0, SWT.BOTTOM);
		fd.left = new FormAttachment(behaviorCanvas, 0, SWT.LEFT);
		fd.right = new FormAttachment(behaviorCanvas, 0, SWT.RIGHT);
		businessBehaviorCompo.setLayoutData(fd);
		businessBehaviorCompo.setLayout(rd);

		// Business + passive
		fd = new FormData();
		fd.top = new FormAttachment(businessCanvas, 0, SWT.TOP);
		fd.bottom = new FormAttachment(businessCanvas, 0, SWT.BOTTOM);
		fd.left = new FormAttachment(passiveCanvas, 0, SWT.LEFT);
		fd.right = new FormAttachment(passiveCanvas, 0, SWT.RIGHT);
		businessPassiveCompo.setLayoutData(fd);
		businessPassiveCompo.setLayout(rd);



		// application + active
		fd = new FormData();
		fd.top = new FormAttachment(applicationCanvas, 0, SWT.TOP);
		fd.bottom = new FormAttachment(applicationCanvas, 0, SWT.BOTTOM);
		fd.left = new FormAttachment(activeCanvas, 0, SWT.LEFT);
		fd.right = new FormAttachment(activeCanvas, 0, SWT.RIGHT);
		applicationActiveCompo.setLayoutData(fd);
		applicationActiveCompo.setLayout(rd);


		// application + behavior
		fd = new FormData();
		fd.top = new FormAttachment(applicationCanvas, 0, SWT.TOP);
		fd.bottom = new FormAttachment(applicationCanvas, 0, SWT.BOTTOM);
		fd.left = new FormAttachment(behaviorCanvas, 0, SWT.LEFT);
		fd.right = new FormAttachment(behaviorCanvas, 0, SWT.RIGHT);
		applicationBehaviorCompo.setLayoutData(fd);
		applicationBehaviorCompo.setLayout(rd);

		// application + passive
		fd = new FormData();
		fd.top = new FormAttachment(applicationCanvas, 0, SWT.TOP);
		fd.bottom = new FormAttachment(applicationCanvas, 0, SWT.BOTTOM);
		fd.left = new FormAttachment(passiveCanvas, 0, SWT.LEFT);
		fd.right = new FormAttachment(passiveCanvas, 0, SWT.RIGHT);
		applicationPassiveCompo.setLayoutData(fd);
		applicationPassiveCompo.setLayout(rd);


		// technology + active
		fd = new FormData();
		fd.top = new FormAttachment(technologyCanvas, 0, SWT.TOP);
		fd.bottom = new FormAttachment(technologyCanvas, 0, SWT.BOTTOM);
		fd.left = new FormAttachment(activeCanvas, 0, SWT.LEFT);
		fd.right = new FormAttachment(activeCanvas, 0, SWT.RIGHT);
		technologyActiveCompo.setLayoutData(fd);
		technologyActiveCompo.setLayout(rd);

		// technology + behavior
		fd = new FormData();
		fd.top = new FormAttachment(technologyCanvas, 0, SWT.TOP);
		fd.bottom = new FormAttachment(technologyCanvas, 0, SWT.BOTTOM);
		fd.left = new FormAttachment(behaviorCanvas, 0, SWT.LEFT);
		fd.right = new FormAttachment(behaviorCanvas, 0, SWT.RIGHT);
		technologyBehaviorCompo.setLayoutData(fd);
		technologyBehaviorCompo.setLayout(rd);

		// technology + passive
		fd = new FormData();
		fd.top = new FormAttachment(technologyCanvas, 0, SWT.TOP);
		fd.bottom = new FormAttachment(technologyCanvas, 0, SWT.BOTTOM);
		fd.left = new FormAttachment(passiveCanvas, 0, SWT.LEFT);
		fd.right = new FormAttachment(passiveCanvas, 0, SWT.RIGHT);
		technologyPassiveCompo.setLayoutData(fd);
		technologyPassiveCompo.setLayout(rd);

		// physical + active
		fd = new FormData();
		fd.top = new FormAttachment(physicalCanvas, 0, SWT.TOP);
		fd.bottom = new FormAttachment(physicalCanvas, 0, SWT.BOTTOM);
		fd.left = new FormAttachment(activeCanvas, 0, SWT.LEFT);
		fd.right = new FormAttachment(activeCanvas, 0, SWT.RIGHT);
		physicalActiveCompo.setLayoutData(fd);
		physicalActiveCompo.setLayout(rd);

		// physical + behavior
		fd = new FormData();
		fd.top = new FormAttachment(physicalCanvas, 0, SWT.TOP);
		fd.bottom = new FormAttachment(physicalCanvas, 0, SWT.BOTTOM);
		fd.left = new FormAttachment(behaviorCanvas, 0, SWT.LEFT);
		fd.right = new FormAttachment(behaviorCanvas, 0, SWT.RIGHT);
		physicalBehaviorCompo.setLayoutData(fd);
		physicalBehaviorCompo.setLayout(rd);

		// physical + passive
		fd = new FormData();
		fd.top = new FormAttachment(physicalCanvas, 0, SWT.TOP);
		fd.bottom = new FormAttachment(physicalCanvas, 0, SWT.BOTTOM);
		fd.left = new FormAttachment(passiveCanvas, 0, SWT.LEFT);
		fd.right = new FormAttachment(passiveCanvas, 0, SWT.RIGHT);
		physicalPassive.setLayoutData(fd);
		physicalPassive.setLayout(rd);

		// implementation
		fd = new FormData();
		fd.top = new FormAttachment(implementationCanvas, 0, SWT.TOP);
		fd.bottom = new FormAttachment(implementationCanvas, 0, SWT.BOTTOM);
		fd.left = new FormAttachment(passiveCanvas, 0, SWT.LEFT);
		fd.right = new FormAttachment(activeCanvas, 0, SWT.RIGHT);
		implementationCompo.setLayoutData(fd);
		rd = new RowLayout(SWT.HORIZONTAL);
		rd.center = true;
		rd.fill = true;
		rd.justify = true;
		rd.wrap = true;
		rd.marginBottom = 5;
		rd.marginTop = 7;
		rd.marginLeft = 5;
		rd.marginRight = 5;
		rd.spacing = 0;
		implementationCompo.setLayout(rd);

		// motivation
		fd = new FormData();
		fd.top = new FormAttachment(motivationCanvas, 20, SWT.TOP);
		fd.bottom = new FormAttachment(motivationCanvas, 0, SWT.BOTTOM);
		fd.left = new FormAttachment(motivationCanvas, 0, SWT.LEFT);
		fd.right = new FormAttachment(motivationCanvas, 0, SWT.RIGHT);
		motivationCompo.setLayoutData(fd);
		rd = new RowLayout(SWT.VERTICAL);
		rd.center = true;
		rd.fill = true;
		rd.justify = true;
		rd.wrap = true;
		rd.marginBottom = 5;
		rd.marginTop = 5;
		rd.marginLeft = 20;
		rd.marginRight = 5;
		rd.spacing = 0;
		motivationCompo.setLayout(rd);

		// other
		fd = new FormData();
		fd.top = new FormAttachment(otherCanvas, 0, SWT.TOP);
		fd.bottom = new FormAttachment(otherCanvas, 0, SWT.BOTTOM);
		fd.left = new FormAttachment(otherCanvas, 0, SWT.LEFT);
		fd.right = new FormAttachment(otherCanvas, 0, SWT.RIGHT);
		otherCompo.setLayoutData(fd);
		rd = new RowLayout(SWT.HORIZONTAL);
		rd.center = true;
		rd.fill = true;
		rd.justify = true;
		rd.wrap = true;
		rd.marginBottom = 5;
		rd.marginTop = 5;
		rd.marginLeft = 5;
		rd.marginRight = 5;
		rd.spacing = 0;
		otherCompo.setLayout(rd);
	}

	/*
	private void createCompoComposites() {
		compoContainers = new Composite(grpFilter, SWT.NONE);
		compoContainers.setBackground(GROUP_BACKGROUND_COLOR);
		FormData fd = new FormData();
		fd.top = new FormAttachment(0);
		fd.left = new FormAttachment(0, 135);
		fd.right = new FormAttachment(100, -10);
		fd.bottom = new FormAttachment(100, -10);
		compoContainers.setLayoutData(fd);
		compoContainers.setLayout(new FormLayout());

		Label sorryLabel = new Label(compoContainers, SWT.NONE);
		sorryLabel.setBackground(GROUP_BACKGROUND_COLOR);
		sorryLabel.setText("Not yet implemented, sorry ...");
		fd = new FormData();
		fd.top = new FormAttachment(35);
		fd.left = new FormAttachment(0, 30);
		sorryLabel.setLayoutData(fd);
	}
	 */

	/*
	private void createCompoFolders() {
		compoFolders = new Composite(grpFilter, SWT.NONE);
		compoFolders.setBackground(GROUP_BACKGROUND_COLOR);
		FormData fd = new FormData();
		fd.top = new FormAttachment(0);
		fd.left = new FormAttachment(0, 135);
		fd.right = new FormAttachment(100, -10);
		fd.bottom = new FormAttachment(100, -10);
		compoFolders.setLayoutData(fd);
		compoFolders.setLayout(new FormLayout());

		Label folderTypeLabel = new Label(compoFolders, SWT.NONE);
		folderTypeLabel.setBackground(GROUP_BACKGROUND_COLOR);
		folderTypeLabel.setText("Select folders type to display :");
		fd = new FormData();
		fd.top = new FormAttachment(0);
		fd.left = new FormAttachment(0, 30);
		folderTypeLabel.setLayoutData(fd);

		strategyFolders = new Button(compoFolders, SWT.CHECK);
		strategyFolders.setBackground(GROUP_BACKGROUND_COLOR);
		strategyFolders.setText("Strategy");
		fd = new FormData();
		fd.top = new FormAttachment(folderTypeLabel, 5);
		fd.left = new FormAttachment(folderTypeLabel, 20, SWT.LEFT);
		strategyFolders.setLayoutData(fd);
		strategyFolders.addListener(SWT.MouseUp, getFoldersListener);

		businessFolders = new Button(compoFolders, SWT.CHECK);
		businessFolders.setBackground(GROUP_BACKGROUND_COLOR);
		businessFolders.setText("Business");
		fd = new FormData();
		fd.top = new FormAttachment(strategyFolders, 5);
		fd.left = new FormAttachment(strategyFolders, 0, SWT.LEFT);
		businessFolders.setLayoutData(fd);
		businessFolders.addListener(SWT.MouseUp, getFoldersListener);

		applicationFolders = new Button(compoFolders, SWT.CHECK);
		applicationFolders.setBackground(GROUP_BACKGROUND_COLOR);
		applicationFolders.setText("Application");
		fd = new FormData();
		fd.top = new FormAttachment(businessFolders, 5);
		fd.left = new FormAttachment(strategyFolders, 0, SWT.LEFT);
		applicationFolders.setLayoutData(fd);
		applicationFolders.addListener(SWT.MouseUp, getFoldersListener);

		technologyFolders = new Button(compoFolders, SWT.CHECK);
		technologyFolders.setBackground(GROUP_BACKGROUND_COLOR);
		technologyFolders.setText("Technology && Physical");
		fd = new FormData();
		fd.top = new FormAttachment(applicationFolders, 5);
		fd.left = new FormAttachment(strategyFolders, 0, SWT.LEFT);
		technologyFolders.setLayoutData(fd);
		technologyFolders.addListener(SWT.MouseUp, getFoldersListener);

		motivationFolders = new Button(compoFolders, SWT.CHECK);
		motivationFolders.setBackground(GROUP_BACKGROUND_COLOR);
		motivationFolders.setText("Motivation");
		fd = new FormData();
		fd.top = new FormAttachment(technologyFolders, 5);
		fd.left = new FormAttachment(strategyFolders, 0, SWT.LEFT);
		motivationFolders.setLayoutData(fd);
		motivationFolders.addListener(SWT.MouseUp, getFoldersListener);

		implementationFolders = new Button(compoFolders, SWT.CHECK);
		implementationFolders.setBackground(GROUP_BACKGROUND_COLOR);
		implementationFolders.setText("Implementation & Migration");
		fd = new FormData();
		fd.top = new FormAttachment(motivationFolders, 5);
		fd.left = new FormAttachment(strategyFolders, 0, SWT.LEFT);
		implementationFolders.setLayoutData(fd);
		implementationFolders.addListener(SWT.MouseUp, getFoldersListener);

		otherFolders = new Button(compoFolders, SWT.CHECK);
		otherFolders.setBackground(GROUP_BACKGROUND_COLOR);
		otherFolders.setText("Other");
		fd = new FormData();
		fd.top = new FormAttachment(implementationFolders, 5);
		fd.left = new FormAttachment(strategyFolders, 0, SWT.LEFT);
		otherFolders.setLayoutData(fd);
		otherFolders.addListener(SWT.MouseUp, getFoldersListener);
	}
	 */

	private void createCompoViews() {
		this.compoViews = new Composite(this.grpFilter, SWT.NONE);
		this.compoViews.setBackground(GROUP_BACKGROUND_COLOR);
		FormData fd = new FormData();
		fd.top = new FormAttachment(0);
		fd.left = new FormAttachment(0, 135);
		fd.right = new FormAttachment(100, -10);
		fd.bottom = new FormAttachment(100, -10);
		this.compoViews.setLayoutData(fd);
		this.compoViews.setLayout(new FormLayout());

		Label viewTypeLabel = new Label(this.compoViews, SWT.NONE);
		viewTypeLabel.setBackground(GROUP_BACKGROUND_COLOR);
		viewTypeLabel.setText("Select views type to display :");
		fd = new FormData();
		fd.top = new FormAttachment(0);
		fd.left = new FormAttachment(0, 30);
		viewTypeLabel.setLayoutData(fd);

		this.archimateViews = new Button(this.compoViews, SWT.CHECK);
		this.archimateViews.setBackground(GROUP_BACKGROUND_COLOR);
		this.archimateViews.setText("Archimate views");
		fd = new FormData();
		fd.top = new FormAttachment(viewTypeLabel, 5);
		fd.left = new FormAttachment(viewTypeLabel, 20, SWT.LEFT);
		this.archimateViews.setLayoutData(fd);
		this.archimateViews.addListener(SWT.MouseUp, this.getViewsListener); 

		this.canvasViews = new Button(this.compoViews, SWT.CHECK);
		this.canvasViews.setBackground(GROUP_BACKGROUND_COLOR);
		this.canvasViews.setText("Canvas");
		fd = new FormData();
		fd.top = new FormAttachment(this.archimateViews, 5);
		fd.left = new FormAttachment(viewTypeLabel, 20, SWT.LEFT);
		this.canvasViews.setLayoutData(fd);
		this.canvasViews.addListener(SWT.MouseUp, this.getViewsListener); 

		this.sketchViews = new Button(this.compoViews, SWT.CHECK);
		this.sketchViews.setBackground(GROUP_BACKGROUND_COLOR);
		this.sketchViews.setText("Sketch views");
		fd = new FormData();
		fd.top = new FormAttachment(this.canvasViews, 5);
		fd.left = new FormAttachment(viewTypeLabel, 20, SWT.LEFT);
		this.sketchViews.setLayoutData(fd);
		this.sketchViews.addListener(SWT.MouseUp, this.getViewsListener); 
	}

	private void createGrpComponents() {
		this.grpComponent = new Group(this.compoRightBottom, SWT.NONE);
		this.grpComponent.setBackground(GROUP_BACKGROUND_COLOR);
		this.grpComponent.setFont(GROUP_TITLE_FONT);
		this.grpComponent.setText("Select the component to import : ");
		FormData fd = new FormData();
		fd.top = new FormAttachment(this.grpFilter, 10);
		fd.left = new FormAttachment(0);
		fd.right = new FormAttachment(100);
		fd.bottom = new FormAttachment(100);
		this.grpComponent.setLayoutData(fd);
		this.grpComponent.setLayout(new FormLayout());
		
		this.lblComponents = new Label(this.grpComponent, SWT.NONE);
		this.lblComponents.setBackground(GROUP_BACKGROUND_COLOR);
		fd = new FormData();
		fd.top = new FormAttachment(0, 10);
		fd.left = new FormAttachment(10);
		fd.right = new FormAttachment(100, -10);
		this.lblComponents.setLayoutData(fd);

		SelectionListener redrawTblComponents = new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				try {
					if ( DBGuiImportComponent.this.importConnection.isConnected() ) {
						if ( DBGuiImportComponent.this.compoElements.isVisible() )
							getElements();
						//else if ( compoContainers.isVisible() )
						//  getContainers();
						//else if ( compoFolders.isVisible() )
						//    getFolders();
						else if ( DBGuiImportComponent.this.compoViews.isVisible() )
							getViews();
					}
				} catch (Exception err) {
					DBGui.popup(Level.ERROR, "An exception has been raised during the import.", err);
				} 
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent event) {
				widgetSelected(event);                    
			}
		};

		this.hideAlreadyInModel = new Button(this.grpComponent, SWT.CHECK);
		this.hideAlreadyInModel.setBackground(GROUP_BACKGROUND_COLOR);
		this.hideAlreadyInModel.setText("Hide components already in model");
		this.hideAlreadyInModel.setSelection(true);
		this.hideAlreadyInModel.addSelectionListener(redrawTblComponents);

		this.hideOption = new Button(this.grpComponent, SWT.CHECK);
		this.hideOption.setBackground(GROUP_BACKGROUND_COLOR);
		this.hideOption.setText("Hide components with empty names");
		this.hideOption.setSelection(true);
		this.hideOption.addSelectionListener(redrawTblComponents);
		
		this.tblComponents = new Table(this.grpComponent, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.MULTI);
		this.tblComponents.setLinesVisible(true);
		this.tblComponents.setHeaderVisible(true);
		this.tblComponents.setBackground(GROUP_BACKGROUND_COLOR);
		this.tblComponents.addListener(SWT.Selection, new Listener() {
			@Override
            public void handleEvent(Event event) {
				if ( DBGuiImportComponent.this.tblComponents.getItemCount() < 2 ) {
					DBGuiImportComponent.this.lblComponents.setText(DBGuiImportComponent.this.tblComponents.getItemCount()+" component matches your criterias");
				} else {
					DBGuiImportComponent.this.lblComponents.setText(DBGuiImportComponent.this.tblComponents.getItemCount()+" components match your criterias");
				}
				
				if ( DBGuiImportComponent.this.tblComponents.getSelectionCount() == 0) {
					DBGuiImportComponent.this.lblComponents.setText(DBGuiImportComponent.this.lblComponents.getText()+".");
				} else {
					DBGuiImportComponent.this.lblComponents.setText(DBGuiImportComponent.this.lblComponents.getText()+" ("+DBGuiImportComponent.this.tblComponents.getSelectionCount()+" selected).");
				}
				
				DBGuiImportComponent.this.btnDoAction.setEnabled(true);		// as soon a component is selected, we can import it
			}
		});
		
		TableColumn colName = new TableColumn(this.tblComponents, SWT.NONE);
		colName.setText("Name");
		colName.setWidth(150);
		TableColumn colDocumentation = new TableColumn(this.tblComponents, SWT.NONE);
		colDocumentation.setText("Documentation");
		colDocumentation.setWidth(300);
		
		this.tblComponents.addListener(SWT.MouseDoubleClick, new Listener() {
			@Override
            public void handleEvent(Event event) {
				if ( DBGuiImportComponent.this.btnDoAction.getEnabled() )
					DBGuiImportComponent.this.btnDoAction.notifyListeners(SWT.Selection, new Event());
			}
		});		

		fd = new FormData();
		fd.bottom = new FormAttachment(100, -5);
		fd.left = new FormAttachment(50, 5);
		this.hideOption.setLayoutData(fd);

		fd = new FormData();
		fd.bottom = new FormAttachment(100, -5);
		fd.right = new FormAttachment(50, -5);
		this.hideAlreadyInModel.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(this.lblComponents, 10);
		fd.left = new FormAttachment(10);
		fd.right = new FormAttachment(90);
		fd.bottom = new FormAttachment(this.hideAlreadyInModel, -5);
		this.tblComponents.setLayoutData(fd);
	}

	void getElements() throws Exception {
		this.compoElements.setVisible(true);
		//compoContainers.setVisible(false);
		//compoFolders.setVisible(false);
		this.compoViews.setVisible(false);

		this.tblComponents.removeAll();

		if ( logger.isDebugEnabled() ) logger.debug("Getting elements");

		StringBuilder inList = new StringBuilder();
		ArrayList<String> classList = new ArrayList<String>();
		for (ComponentLabel label: this.allElementLabels) {
			if ( (boolean)label.getLabel().getData("isSelected") ) {
				inList.append(inList.length()==0 ? "?" : ", ?");
				classList.add(label.getElementClassname());
			}
		}
		
		this.hideOption.setText("Hide components with empty names");
		String addOn = "";
		if ( this.hideOption.getSelection() ) {
			if ( this.selectedDatabase.getDriver().equals("oracle") ) {
				addOn = " AND LENGTH(name) <> 0";
			} else {
				addOn = " AND name <> ''";
			}
		}
			
		addOn += " AND version = (SELECT MAX(version) FROM "+this.selectedDatabase.getSchemaPrefix()+"elements WHERE id = e.id)";
		addOn += " ORDER BY NAME";
		
		if ( logger.isTraceEnabled() ) {
			logger.trace("   inList = "+inList.toString());
			logger.trace("   addOn = "+addOn);
		}

		if ( inList.length() != 0 ) {
			@SuppressWarnings("resource")
			ResultSet result = null;
			try {
				if ( this.filterName.getText().length() == 0 )
					result = this.importConnection.select("SELECT id, class, name, documentation FROM "+this.selectedDatabase.getSchemaPrefix()+"elements e WHERE class IN ("+inList.toString()+")"+addOn, classList);
				else {
					if ( this.ignoreCase.getSelection() )
						result = this.importConnection.select("SELECT id, class, name, documentation FROM "+this.selectedDatabase.getSchemaPrefix()+"elements e WHERE class IN ("+inList.toString()+") AND UPPER(name) like ?"+addOn, classList, "%"+this.filterName.getText().toUpperCase()+"%");
					else
						result = this.importConnection.select("SELECT id, class, name, documentation FROM "+this.selectedDatabase.getSchemaPrefix()+"elements e WHERE class IN ("+inList.toString()+") AND name like ?"+addOn, classList, "%"+this.filterName.getText()+"%");
				}
	
				while (result.next()) {
					if ( !this.hideAlreadyInModel.getSelection() || (this.importedModel.getAllElements().get(result.getString("id"))==null))
						createTableItem(this.tblComponents, result.getString("id"), result.getString("Class"), result.getString("name"), result.getString("documentation"));
				}
			} finally {
				if ( result != null ) {
					result.close();
					result = null;
				}
			}
		}
		
		if ( this.tblComponents.getItemCount() < 2 ) {
			this.lblComponents.setText(this.tblComponents.getItemCount()+" component matches your criterias");
		} else {
			this.lblComponents.setText(this.tblComponents.getItemCount()+" components match your criterias");
		}
		
		if ( this.tblComponents.getSelectionCount() == 0) {
			this.lblComponents.setText(this.lblComponents.getText()+".");
		} else {
			this.lblComponents.setText(this.lblComponents.getText()+" ("+this.tblComponents.getSelectionCount()+" selected).");
		}

		this.btnDoAction.setEnabled(false);
	}

	/*
	private void getFolders() throws Exception {
		compoElements.setVisible(false);
		compoContainers.setVisible(false);
		compoFolders.setVisible(true);
		compoViews.setVisible(false);

		logger.if ( logger.isTraceEnabled() ) logger.trace("emptying tblComponents");
		tblComponents.removeAll();

		if ( logger.isTraceEnabled() ) logger.trace("getting folders");

		StringBuilder inList = new StringBuilder();
		ArrayList<String> typeList = new ArrayList<String>();
		if ( strategyFolders.getSelection() ) {
			inList.append(inList.length()==0 ? "?" : ", ?");
			typeList.add(String.valueOf(FolderType.STRATEGY_VALUE));
		}
		if ( businessFolders.getSelection() ) {
			inList.append(inList.length()==0 ? "?" : ", ?");
			typeList.add(String.valueOf(FolderType.BUSINESS_VALUE));
		}
		if ( applicationFolders.getSelection() ) {
			inList.append(inList.length()==0 ? "?" : ", ?");
			typeList.add(String.valueOf(FolderType.APPLICATION_VALUE));
		}
		if ( technologyFolders.getSelection() ) {
			inList.append(inList.length()==0 ? "?" : ", ?");
			typeList.add(String.valueOf(FolderType.TECHNOLOGY_VALUE));
		}
		if ( motivationFolders.getSelection() ) {
			inList.append(inList.length()==0 ? "?" : ", ?");
			typeList.add(String.valueOf(FolderType.MOTIVATION_VALUE));
		}
		if ( implementationFolders.getSelection() ) {
			inList.append(inList.length()==0 ? "?" : ", ?");
			typeList.add(String.valueOf(FolderType.IMPLEMENTATION_MIGRATION_VALUE));
		}
		if ( otherFolders.getSelection() ) {
			inList.append(inList.length()==0 ? "?" : ", ?");
			typeList.add(String.valueOf(FolderType.OTHER_VALUE));
		}

		hideOption.setText("Hide top level folders");
		String addOn = "";
		if ( hideOption.getSelection() )
		    addOn = " AND type = 0";
		addOn += " ORDER BY NAME";

		if ( inList.length() != 0 ) {
			ResultSet result;

			if ( filterName.getText().length() == 0 )
				result = database.select("SELECT id, name, documentation FROM "+selectedDatabase.getSchemaPrefix()+"folders WHERE root_type IN ("+inList.toString()+")"+addOn, typeList);
			else
				result = database.select("SELECT id, name, documentation FROM "+selectedDatabase.getSchemaPrefix()+"folders WHERE root_type IN ("+inList.toString()+") AND name like ?"+addOn, typeList, "%"+filterName.getText()+"%");

			while (result.next()) {
			    if ( !hideAlreadyInModel.getSelection() || (importedModel.getAllFolders().get(result.getString("id"))==null))
			        createTableItem(tblComponents, result.getString("id"), "Folder", result.getString("name"), result.getString("documentation"));
			}
			result.close();
		}
		
		if ( tblComponents.getItemCount() < 2 ) {
			lblComponents.setText(tblComponents.getItemCount()+" component matches your criterias");
		} else {
			lblComponents.setText(tblComponents.getItemCount()+" components match your criterias");
		}
		
		if ( tblComponents.getSelectionCount() == 0) {
			lblComponents.setText(lblComponents.getText()+".");
		} else {
			lblComponents.setText(lblComponents.getText()+" ("+tblComponents.getSelectionCount()+" selected).");
		}

		btnDoAction.setEnabled(false);
	}
	 */

	void getViews() throws Exception {
		this.compoElements.setVisible(false);
		//compoContainers.setVisible(false);
		//compoFolders.setVisible(false);
		this.compoViews.setVisible(true);

		this.tblComponents.removeAll();

		if ( logger.isDebugEnabled() ) logger.debug("Getting views");

		StringBuilder inList = new StringBuilder();
		ArrayList<String> classList = new ArrayList<String>();
		if ( this.archimateViews.getSelection() ) {
			inList.append(inList.length()==0 ? "?" : ", ?");
			classList.add("ArchimateDiagramModel");
		}
		if ( this.canvasViews.getSelection() ) {
			inList.append(inList.length()==0 ? "?" : ", ?");
			classList.add("CanvasModel");
		}
		if ( this.sketchViews.getSelection() ) {
			inList.append(inList.length()==0 ? "?" : ", ?");
			classList.add("SketchModel");
		}

		this.hideOption.setText("Hide default views");
		String addOn = "";
		if ( this.hideOption.getSelection() )
			addOn = " AND name <> 'Default View'";
		
		addOn += " AND version = (SELECT MAX(version) FROM "+this.selectedDatabase.getSchemaPrefix()+"views WHERE id = v.id)";
		addOn += " ORDER BY NAME";

		if ( inList.length() != 0 ) {
			@SuppressWarnings("resource")
			ResultSet result = null;
			try {
				if ( this.filterName.getText().length() == 0 )
					result = this.importConnection.select("SELECT id, class, name, documentation FROM "+this.selectedDatabase.getSchemaPrefix()+"views v WHERE class IN ("+inList.toString()+")"+addOn, classList);
				else
					result = this.importConnection.select("SELECT id, class, name, documentation FROM "+this.selectedDatabase.getSchemaPrefix()+"views v WHERE class IN ("+inList.toString()+") AND name like ?"+addOn, classList, "%"+this.filterName.getText()+"%");
	
				while (result.next()) {
					if ( !this.hideAlreadyInModel.getSelection() || (this.importedModel.getAllViews().get(result.getString("id"))==null))
						createTableItem(this.tblComponents, result.getString("id"), result.getString("Class"), result.getString("name"), result.getString("documentation"));
				}
			} finally {
				if ( result != null ) {
					result.close();
					result = null;
				}
			}
		}
		
		if ( this.tblComponents.getItemCount() < 2 ) {
			this.lblComponents.setText(this.tblComponents.getItemCount()+" component matches your criterias");
		} else {
			this.lblComponents.setText(this.tblComponents.getItemCount()+" components match your criterias");
		}
		
		if ( this.tblComponents.getSelectionCount() == 0) {
			this.lblComponents.setText(this.lblComponents.getText()+".");
		} else {
			this.lblComponents.setText(this.lblComponents.getText()+" ("+this.tblComponents.getSelectionCount()+" selected).");
		}

		this.btnDoAction.setEnabled(false);
	}



	private static void createTableItem(Table table, String id, String className, String name, String documentation) {
		TableItem item = new TableItem(table, SWT.NONE);
		item.setData("id", id);
		item.setText(0, "   "+name);
		item.setText(1, "   "+documentation);
		if ( className.toUpperCase().startsWith("CANVAS") )
			item.setImage(DBCanvasFactory.getImage(className));
		else
			item.setImage(DBArchimateFactory.getImage(className));
		//TODO: add the properties in a popup as several components may have the same name !!! 
	}


	void doImport() throws Exception {
		if ( logger.isDebugEnabled() ) {
			if ( getOptionValue() )
				logger.debug("Importing "+this.tblComponents.getSelectionCount()+" component(s).");
			else
				logger.debug("Importing a copy of "+this.tblComponents.getSelectionCount()+" component(s).");
		}

		List<Object> imported = new ArrayList<Object>();
		int done = 0;
		
		try {
			for ( TableItem tableItem: this.tblComponents.getSelection() ) {
				String id = (String)tableItem.getData("id");
				String name = tableItem.getText(0).trim();

				setMessage("("+(++done)+"/"+this.tblComponents.getSelectionCount()+") Importing \""+name+"\".");
				
				if ( this.compoElements.getVisible() )
					imported.add(this.importConnection.importElementFromId(this.importedModel, this.selectedView, id, 0, !getOptionValue(), true));
				//else if ( compoContainers.getVisible() )
				//	database.importContainerFromId(importedModel, id, !getOptionValue());
				//	database.importFolder(importedModel, id, !getOptionValue());
				else if ( this.compoViews.getVisible() ) {
					IDiagramModel view = this.importConnection.importViewFromId(this.importedModel, /*this.selectedFolder,*/ id, 0, !getOptionValue(), true);
					if ( view != null )
						imported.add(view);
				}
			}
		} catch(RuntimeException e) {
			popup(Level.ERROR, "Couldn't import component.", e);
		} finally {
			// we do not catch the exception if any, but we need to close the popup
			closeMessage();
		}

		if ( !imported.isEmpty() ) {
			// We select the element in the model tree
			ITreeModelView treeView = (ITreeModelView)ViewManager.showViewPart(ITreeModelView.ID, true);
			if(treeView != null)
				treeView.getViewer().setSelection(new StructuredSelection(imported));
		}

		// we redraw the tblComponents to unselect the items (and hide the newly imported components if the option is selected)
		this.hideAlreadyInModel.notifyListeners(SWT.Selection, new Event());
	}

	private class ComponentLabel {
	    private Label label;

		ComponentLabel(Composite parent, String toolTip) {
			this.label = new Label(parent, SWT.NONE);
			this.label.setSize(100,  100);
			this.label.setToolTipText(toolTip);
			this.label.setImage(DBArchimateFactory.getImage(getElementClassname()));
			this.label.addPaintListener(this.redraw);
			this.label.addListener(SWT.MouseUp, DBGuiImportComponent.this.getElementsListener);
			this.label.setData("isSelected", false);
		}

		private PaintListener redraw = new PaintListener() {
			@Override
			public void paintControl(PaintEvent event)
			{
				if ( (boolean)ComponentLabel.this.getLabel().getData("isSelected") )
				    ComponentLabel.this.getLabel().setBackground(GREY_COLOR);
				//event.gc.drawRoundRectangle(0, 0, 16, 16, 2, 2);
				else
				    ComponentLabel.this.getLabel().setBackground(null);
			}
		};

		public String getElementClassname() {
			return this.label.getToolTipText().replaceAll(" ",  "");
		}
		
		public Label getLabel() {
		    return this.label;
		}
	}

	Listener getElementsListener = new Listener() {
		@Override
		public void handleEvent(Event event) {
			Label label = (Label)event.widget;
			label.setData("isSelected", !(boolean)label.getData("isSelected"));
			label.redraw();
			try {
				getElements();
			} catch (Exception err) {
				DBGui.popup(Level.ERROR, "An exception has been raised during the import.", err);
			}
		}
	};

	/*
    private Listener getFoldersListener = new Listener() {
    	@Override
    	public void handleEvent(Event event) {
    		try {
				getFolders();
			} catch (Exception err) {
				DBGui.popup(Level.ERROR, "An exception has been raised during the import.", err);
			}
    	}
    };
	 */

	private Listener getViewsListener = new Listener() {
		@Override
		public void handleEvent(Event event) {
			try {
				getViews();
			} catch (Exception err) {
				DBGui.popup(Level.ERROR, "An exception has been raised during the import.", err);
			}
		}
	};
}
