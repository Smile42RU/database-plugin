package org.archicontribs.database.menu;

import org.archicontribs.database.DBLogger;
import org.archicontribs.database.GUI.DBGuiExportModel;
import org.archicontribs.database.model.ArchimateModel;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

public class DBMenuModelExportHandler extends AbstractHandler {
	private static final DBLogger logger = new DBLogger(DBMenu.class);
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Object selection = ((IStructuredSelection)HandlerUtil.getCurrentSelection(event)).getFirstElement();
		ArchimateModel model = (ArchimateModel)selection;
		
		if ( logger.isDebugEnabled() ) logger.debug("Exporting model "+model.getName());

		new DBGuiExportModel(model, "Export model");
		return null;
	}
}
