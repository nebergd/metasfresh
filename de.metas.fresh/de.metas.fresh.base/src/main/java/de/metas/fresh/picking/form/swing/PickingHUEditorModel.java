package de.metas.fresh.picking.form.swing;

import java.util.List;

import org.adempiere.util.Check;

import de.metas.adempiere.form.terminal.context.ITerminalContext;
import de.metas.handlingunits.client.terminal.editor.model.IHUKey;
import de.metas.handlingunits.client.terminal.editor.model.IHUKeyFactory;
import de.metas.handlingunits.client.terminal.editor.model.impl.HUEditorModel;
import de.metas.handlingunits.document.impl.NullHUDocumentLineFinder;
import de.metas.handlingunits.model.I_M_HU;

/*
 * #%L
 * de.metas.fresh.base
 * %%
 * Copyright (C) 2016 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

/**
 * Picking HU editor (model).
 *
 * @author metas-dev <dev@metasfresh.com>
 *
 */
/* package */class PickingHUEditorModel extends HUEditorModel
{
	private IHUSupplier huSupplier;
	private I_M_HU _huToSelect;

	private Boolean _considerAttributes = null;

	public PickingHUEditorModel(final ITerminalContext terminalContext, final I_M_HU huToSelect, final IHUSupplier huSupplier)
	{
		super(terminalContext);

		Check.assumeNotNull(huSupplier, "huSupplier not null");
		this.huSupplier = huSupplier;

		_huToSelect = huToSelect;
	}

	@Override
	public void dispose()
	{
		super.dispose();
		huSupplier = null;
		_huToSelect = null;
	}

	private I_M_HU getHUToSelect()
	{
		return _huToSelect;
	}

	/**
	 * Sets if we shall consider the attributes while searching for matching HUs.
	 *
	 * This method is also automatically fetching the matching HUs.
	 *
	 * @param considerAttributes
	 * @task FRESH-578 #275
	 */
	public void setConsiderAttributes(final boolean considerAttributes)
	{
		_considerAttributes = considerAttributes;
		refreshHUKeys();
	}

	public boolean isConsiderAttributes()
	{
		final Boolean considerAttributes = _considerAttributes;
		return considerAttributes != null && considerAttributes.booleanValue();
	}

	private void refreshHUKeys()
	{
		//
		// Find available HUs
		final List<I_M_HU> hus = huSupplier.retrieveHUs(isConsiderAttributes());
		try
		{
			Thread.sleep(2 * 1000);
		}
		catch (final InterruptedException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		//
		// Create the root HU key containing the available HUs
		final ITerminalContext terminalContext = getTerminalContext();
		final IHUKeyFactory huKeyFactory = terminalContext.getService(IHUKeyFactory.class);
		final IHUKey rootHUKey = huKeyFactory.createRootKey();
		final List<IHUKey> huKeys = huKeyFactory.createKeys(hus, NullHUDocumentLineFinder.instance); // documentLine = null
		rootHUKey.addChildren(huKeys);
		setRootHUKey(rootHUKey);

		//
		// Auto-select the provided HU if any
		final I_M_HU huToSelect = getHUToSelect();
		if (huToSelect != null)
		{
			setSelected(huToSelect);
		}

	}

	/**
	 * Matching HUs supplier.
	 */
	public static interface IHUSupplier
	{
		/**
		 *
		 * @param considerAttributes true if we shall consider the HU attributes while searching for matching HUs
		 * @return matching HUs
		 */
		List<I_M_HU> retrieveHUs(final boolean considerAttributes);
	}
}