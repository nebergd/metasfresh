package de.metas.handlingunits.attribute.impl;

/*
 * #%L
 * de.metas.handlingunits.base
 * %%
 * Copyright (C) 2015 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */


import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import de.metas.logging.LogManager;

import org.adempiere.mm.attributes.api.IAttributesBL;
import org.adempiere.mm.attributes.model.I_M_Attribute;
import org.adempiere.mm.attributes.spi.IAttributeValueCallout;
import org.adempiere.mm.attributes.spi.IAttributeValueContext;
import org.adempiere.mm.attributes.spi.IAttributeValueGenerator;
import org.adempiere.mm.attributes.spi.IAttributeValuesProvider;
import org.adempiere.mm.attributes.spi.NullAttributeValueCallout;
import org.adempiere.mm.attributes.spi.impl.StaticAttributeValuesProvider;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.Check;
import org.adempiere.util.Services;
import org.compiere.model.I_C_UOM;
import org.compiere.model.X_M_Attribute;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.NamePair;

import de.metas.handlingunits.attribute.IAttributeValue;
import de.metas.handlingunits.attribute.IAttributeValueListener;
import de.metas.handlingunits.attribute.IHUAttributesDAO;
import de.metas.handlingunits.attribute.exceptions.InvalidAttributeValueException;
import de.metas.handlingunits.attribute.storage.IAttributeStorage;

/**
 * Generic {@link IAttributeValue} value implementation
 *
 * @author tsa
 *
 */
public abstract class AbstractAttributeValue implements IAttributeValue
{
	protected final transient Logger logger = LogManager.getLogger(getClass());

	private final IAttributeStorage attributeStorage;
	private final I_M_Attribute attribute;

	/**
	 * Value Type.
	 *
	 * See X_M_Attribute.ATTRIBUTEVALUETYPE_*.
	 */
	private final String valueType;

	private final IAttributeValuesProvider _attributeValuesProvider;

	private final CompositeAttributeValueListener listeners = new CompositeAttributeValueListener();

	public AbstractAttributeValue(final IAttributeStorage attributeStorage, final org.compiere.model.I_M_Attribute attribute)
	{
		super();

		Check.assumeNotNull(attributeStorage, "attributeStorage not null");
		this.attributeStorage = attributeStorage;

		Check.assumeNotNull(attribute, "attribute not null");
		this.attribute = InterfaceWrapperHelper.create(attribute, I_M_Attribute.class);

		_attributeValuesProvider = retrieveAttributeValuesProvider(this.attribute);
		if (_attributeValuesProvider != null)
		{
			valueType = _attributeValuesProvider.getAttributeValueType();
			Check.assume(!X_M_Attribute.ATTRIBUTEVALUETYPE_List.equals(valueType),
					"Provider {} shall not return attribute value type List", _attributeValuesProvider);
		}
		else
		{
			valueType = attribute.getAttributeValueType();
		}
	}

	public final IAttributeStorage getAttributeStorage()
	{
		return attributeStorage;
	}

	protected final IHUAttributesDAO getHUAttributesDAO()
	{
		return getAttributeStorage().getHUAttributeStorageFactory().getHUAttributesDAO();
	}

	protected final IAttributeValuesProvider getAttributeValuesProvider()
	{
		if (_attributeValuesProvider == null)
		{
			throw new InvalidAttributeValueException("No AttributeValueProvider was found");
		}
		return _attributeValuesProvider;
	}

	/**
	 * Retrieves {@link IAttributeValuesProvider} to be used for given attribute (if any)
	 *
	 * @param attribute
	 * @return {@link IAttributeValuesProvider} or null
	 */
	private final IAttributeValuesProvider retrieveAttributeValuesProvider(final I_M_Attribute attribute)
	{
		final IAttributeValueGenerator attributeHandler = getAttributeValueGeneratorOrNull();

		//
		// First try: check if attributeHandler is implementing IAttributeValuesProvider and return it if that's the case
		if (attributeHandler instanceof IAttributeValuesProvider)
		{
			return (IAttributeValuesProvider)attributeHandler;
		}
		//
		// Second try: check if our attribute is of type list, in which case we are dealing with standard M_AttributeValues
		else if (X_M_Attribute.ATTRIBUTEVALUETYPE_List.equals(attribute.getAttributeValueType()))
		{
			return new StaticAttributeValuesProvider();
		}
		//
		// Fallback: there is no IAttributeValuesProvider because attribute does not support Lists
		else
		{
			return null;
		}
	}

	@Override
	public String toString()
	{
		final I_M_Attribute attribute = getM_Attribute();
		final String name = attribute == null ? "?" : attribute.getName();
		final Object value = getValue();

		return getClass().getSimpleName() + "["
				+ name + "=" + value
				+ ", type=" + valueType
				+ "]";
	}

	//@formatter:off
	protected abstract void setInternalValueString(final String value);
	protected abstract String getInternalValueString();
	protected abstract void setInternalValueStringInitial(final String value);
	protected abstract String getInternalValueStringInitial();
	//@formatter:on

	//@formatter:off
	protected abstract void setInternalValueNumber(final BigDecimal value);
	protected abstract BigDecimal getInternalValueNumber();
	protected abstract void setInternalValueNumberInitial(final BigDecimal value);
	protected abstract BigDecimal getInternalValueNumberInitial();
	//@formatter:on

	//@formatter:off
	protected abstract void setInternalValueDate(final Date value);
	protected abstract Date getInternalValueDate();
	protected abstract void setInternalValueDateInitial(final Date value);
	protected abstract Date getInternalValueDateInitial();
	//@formatter:on

	@Override
	public final I_M_Attribute getM_Attribute()
	{
		return attribute;
	}

	@Override
	public final String getAttributeValueType()
	{
		return valueType;
	}

	@Override
	public final void setValue(final IAttributeValueContext attributeValueContext, final Object value)
	{
		final BigDecimal valueBD;
		final String valueStr;
		final Date valueDate;

		final Object valueOld;
		final Object valueNew;

		if (X_M_Attribute.ATTRIBUTEVALUETYPE_Number.equals(valueType))
		{
			valueStr = null;
			valueBD = valueToNumber(value);
			valueDate = null;

			valueOld = getInternalValueNumber();
			valueNew = valueBD;
		}
		else if (X_M_Attribute.ATTRIBUTEVALUETYPE_StringMax40.equals(valueType))
		{
			valueStr = valueToString(value);
			valueBD = null;
			valueDate = null;

			valueOld = getInternalValueString();
			valueNew = valueStr;
		}
		else if (X_M_Attribute.ATTRIBUTEVALUETYPE_Date.equals(valueType))
		{
			valueStr = null;
			valueBD = null;
			valueDate = valueToDate(value);
			
			valueOld = getInternalValueDate();
			valueNew = valueDate;
		}
		else
		{
			throw new InvalidAttributeValueException("Value type '" + valueType + "' not supported for " + attribute);
		}

		//
		// If nothing changed, then it's pointless to set the value and notify the listeners
		// NOTE: also this is converting the case when setting a NULL value, attribute is mandatory, but also value is also NULL... so it's pointless to throw an exception
		if (Check.equals(valueOld, valueNew))
		{
			return;
		}

		//
		// If attribute is mandatory, make sure we are not setting it to NULL
		if (attribute.isMandatory() && Check.isEmpty(valueStr, true) && valueBD == null && valueDate == null)
		{
			throw new InvalidAttributeValueException("Setting null not allowed for mandatory attribute " + attribute);
		}

		//
		// Set internal values
		// FIXME: set order is important because of org.compiere.model.MAttributeInstance.setValueNumber(BigDecimal)
		setInternalValueNumber(valueBD);
		setInternalValueDate(valueDate);
		setInternalValueString(valueStr);
		setInternalValueDate(valueDate);

		//
		// Fire listeners
		onValueChanged(valueOld, valueNew); // internal listener
		listeners.onValueChanged(attributeValueContext, this, valueOld, valueNew);
	}

	@Override
	public final void setValueInitial(final Object value)
	{
		setValueInitial(value, valueType);

	}

	private final void setValueInitial(final Object value, final String valueType)
	{
		final BigDecimal valueBD;
		final String valueStr;
		final Date valueDate;

		final Object valueOld;
		final Object valueNew;

		if (X_M_Attribute.ATTRIBUTEVALUETYPE_Number.equals(valueType))
		{
			valueStr = null;
			valueBD = valueToNumber(value);
			valueDate = null;

			valueOld = getInternalValueNumberInitial();
			valueNew = valueBD;
		}
		else if (X_M_Attribute.ATTRIBUTEVALUETYPE_StringMax40.equals(valueType))
		{
			valueStr = valueToString(value);
			valueBD = null;
			valueDate = null;

			valueOld = getInternalValueStringInitial();
			valueNew = valueStr;
		}
		else if (X_M_Attribute.ATTRIBUTEVALUETYPE_Date.equals(valueType))
		{
			valueStr = null;
			valueBD = null;
			valueDate = valueToDate(value);
			
			valueOld = getInternalValueDateInitial();
			valueNew = valueDate;
		}
		else
		{
			throw new InvalidAttributeValueException("Value type '" + valueType + "' not supported for " + attribute);
		}

		//
		// If nothing changed, then it's pointless to set the value and notify the listeners
		if (Check.equals(valueOld, valueNew))
		{
			return;
		}

		//
		// Set internal values
		// FIXME: set order is important because of org.compiere.model.MAttributeInstance.setValueNumber(BigDecimal)
		setInternalValueNumberInitial(valueBD);
		setInternalValueStringInitial(valueStr);
		setInternalValueDateInitial(valueDate);
	}

	/**
	 * Method called after {@link #setValue(Object)}.
	 *
	 * To be implemented in extending classes.
	 *
	 * @param valueOld old value
	 * @param valueNew new value
	 */
	protected void onValueChanged(final Object valueOld, final Object valueNew)
	{
		// nothing
	}

	@Override
	public final Object getValue()
	{
		if (X_M_Attribute.ATTRIBUTEVALUETYPE_Number.equals(valueType))
		{
			return getInternalValueNumber();
		}
		else if (X_M_Attribute.ATTRIBUTEVALUETYPE_StringMax40.equals(valueType))
		{
			return getInternalValueString();
		}
		else if (X_M_Attribute.ATTRIBUTEVALUETYPE_Date.equals(valueType))
		{
			return getInternalValueDate();
		}
		else
		{
			throw new InvalidAttributeValueException("Value type '" + valueType + "' not supported for " + attribute);
		}
	}

	@Override
	public final Object getValueInitial()
	{
		if (X_M_Attribute.ATTRIBUTEVALUETYPE_Number.equals(valueType))
		{
			return getInternalValueNumberInitial();
		}
		else if (X_M_Attribute.ATTRIBUTEVALUETYPE_StringMax40.equals(valueType))
		{
			return getInternalValueStringInitial();
		}
		else if (X_M_Attribute.ATTRIBUTEVALUETYPE_Date.equals(valueType))
		{
			return getInternalValueDateInitial();
		}
		else
		{
			throw new InvalidAttributeValueException("Value type '" + valueType + "' not supported for " + attribute);
		}
	}

	@Override
	public final BigDecimal getValueAsBigDecimal()
	{
		if (isNumericValue())
		{
			final BigDecimal valueBD = getInternalValueNumber();
			return valueBD == null ? BigDecimal.ZERO : valueBD;
		}
		return BigDecimal.ZERO;
	}

	@Override
	public final int getValueAsInt()
	{
		if (isNumericValue())
		{
			final BigDecimal valueBD = getInternalValueNumber();
			return valueBD == null ? 0 : valueBD.intValueExact();
		}
		return 0;
	}

	@Override
	public final BigDecimal getValueInitialAsBigDecimal()
	{
		if (isNumericValue())
		{
			final BigDecimal valueBD = getInternalValueNumberInitial();
			return valueBD == null ? BigDecimal.ZERO : valueBD;
		}
		return BigDecimal.ZERO;
	}

	@Override
	public final String getValueAsString()
	{
		if (isStringValue())
		{
			return getInternalValueString();
		}
		return null;
	}

	@Override
	public final String getValueInitialAsString()
	{
		if (isStringValue())
		{
			return getInternalValueStringInitial();
		}
		return null;
	}
	
	@Override
	public final Date getValueAsDate()
	{
		if (isDateValue())
		{
			return getInternalValueDate();
		}
		return null;
	}
	
	@Override
	public final Date getValueInitialAsDate()
	{
		if(isDateValue())
		{
			return getInternalValueDateInitial();
		}
		return null;
	}

	protected final BigDecimal valueToNumber(final Object value)
	{
		if (value == null)
		{
			return null;
		}

		if (isList())
		{
			//
			// If our list provider accepts any values
			// Then don't validate it, just return the given value converted to number
			final IAttributeValuesProvider attributeValuesProvider = getAttributeValuesProvider();
			if (attributeValuesProvider.isAllowAnyValue(getAttributeStorage(), getM_Attribute()))
			{
				return toNumber(value);
			}

			final NamePair attributeValue = valueToAttributeValue(value);
			if (attributeValue == null)
			{
				return null;
			}
			Check.assumeInstanceOf(attributeValue, KeyNamePair.class, "attributeValue");
			final int valueInt = ((KeyNamePair)attributeValue).getKey();
			return BigDecimal.valueOf(valueInt);
		}

		return toNumber(value);
	}

	/**
	 * Converts given value to number.
	 *
	 * NOTE: this method is not checking if given value shall be in a list or something, just tries to convert the value to number
	 *
	 * @param value value to be converted to number (not null)
	 * @return number value; never return null
	 */
	private final BigDecimal toNumber(final Object value)
	{
		Check.assumeNotNull(value, "value not null");

		final BigDecimal valueBD;
		if (value instanceof BigDecimal)
		{
			valueBD = (BigDecimal)value;
		}
		else if (value instanceof Integer)
		{
			final int i = (Integer)value;
			valueBD = BigDecimal.valueOf(i);
		}
		else if (value instanceof String)
		{
			try
			{
				valueBD = new BigDecimal(value.toString());
			}
			catch (final Exception e)
			{
				throw new InvalidAttributeValueException("Cannot convert value '" + value + "' (" + value.getClass() + ") to " + BigDecimal.class, e);
			}
		}
		else if (value instanceof KeyNamePair)
		{
			final int key = ((KeyNamePair)value).getKey();
			return BigDecimal.valueOf(key);
		}
		else
		{
			throw new InvalidAttributeValueException("Cannot convert value '" + value + "' (" + value.getClass() + ") to " + BigDecimal.class);
		}

		final MathContext mc = Services.get(IAttributesBL.class).getMathContext(attribute);
		return valueBD.setScale(mc.getPrecision(), mc.getRoundingMode());
	}

	protected final String valueToString(final Object value)
	{
		if (value == null)
		{
			return null;
		}

		if (isList())
		{
			//
			// If our list provider accepts any values
			// Then don't validate it, just return the given value converted to string
			final IAttributeValuesProvider attributeValuesProvider = getAttributeValuesProvider();
			if (attributeValuesProvider.isAllowAnyValue(getAttributeStorage(), getM_Attribute()))
			{
				return value.toString();
			}

			final NamePair attributeValue = valueToAttributeValue(value);
			final String valueStr = attributeValue.getID();
			return valueStr;
		}

		return value.toString();
	}

	/**
	 * 
	 * @param value
	 * @return value as NamePair; never returns null
	 * @throws InvalidAttributeValueException
	 */
	private final NamePair valueToAttributeValue(final Object value)
	{
		Check.assumeNotNull(value, "value not null");

		final String valueStr;
		if (value instanceof NamePair)
		{
			valueStr = ((NamePair)value).getID();
		}
		else if (value instanceof Number)
		{
			final int valueInt = ((Number)value).intValue();

			// Case: while propagating attributes up/down it might me that we get 0 instead of NULL
			// that's why we discard that value here
			if (valueInt == 0)
			{
				return null;
			}
			valueStr = String.valueOf(valueInt);
		}
		else
		{
			valueStr = value.toString();
		}

		final IAttributeValuesProvider attributeValuesProvider = getAttributeValuesProvider();
		final NamePair attributeValue = attributeValuesProvider.getAttributeValueOrNull(getAttributeStorage(), attribute, valueStr);
		if (attributeValue != null)
		{
			return attributeValue;
		}

		throw new InvalidAttributeValueException("Invalid list value '" + value + "' for " + attribute + "."
				+ " Available values are: " + getAvailableValues());
	}
	
	@Override
	public List<? extends NamePair> getAvailableValues()
	{
		final IAttributeValuesProvider attributeValuesProvider = getAttributeValuesProvider();
		final List<? extends NamePair> availableValues = attributeValuesProvider.getAvailableValues(getAttributeStorage(), attribute);
		
		//
		// Case: we are dealing with a high volume attribute values list and we got not values (i.e. they were not loaded)
		// Solution: we are searching and loading just the current value and return it as a singleton list 
		if (attributeValuesProvider.isHighVolume()
				&& availableValues.isEmpty()
				&& getValue() != null)
		{
			final Object value = getValue();
			try
			{
				final NamePair valueNP = valueToAttributeValue(value);
				return Collections.singletonList(valueNP);
			}
			catch (Exception e)
			{
				logger.warn("Failed finding the M_AttributeValue for value=" + value, e);
			}
		}
		
		return availableValues;
	}
	
	protected final Date valueToDate(final Object value)
	{
		if (value == null)
		{
			return null;
		}
		
		if (value instanceof Date)
		{
			return (Date)value;
		}
		else if (value instanceof String)
		{
			try
			{
				return Env.parseTimestamp(value.toString());
			}
			catch (Exception e)
			{
				throw new InvalidAttributeValueException("Cannot convert value '" + value + "' (" + value.getClass() + ") to " + Date.class, e);
			}
		}
		else
		{
			throw new InvalidAttributeValueException("Cannot convert value '" + value + "' (" + value.getClass() + ") to " + Date.class);
		}
	}


	@Override
	public NamePair getNullAttributeValue()
	{
		final IAttributeValuesProvider attributeValuesProvider = getAttributeValuesProvider();
		return attributeValuesProvider.getNullValue(attribute);
	}

	@Override
	public final boolean isNumericValue()
	{
		return X_M_Attribute.ATTRIBUTEVALUETYPE_Number.equals(valueType);
	}

	@Override
	public final boolean isStringValue()
	{
		return X_M_Attribute.ATTRIBUTEVALUETYPE_StringMax40.equals(valueType);
	}
	
	public final boolean isDateValue()
	{
		return X_M_Attribute.ATTRIBUTEVALUETYPE_Date.equals(valueType);
	}

	@Override
	public final boolean isList()
	{
		return _attributeValuesProvider != null;
	}

	@Override
	public final boolean isEmpty()
	{
		final Object value = getValue();

		if (value instanceof BigDecimal)
		{
			return Check.isEmpty((BigDecimal)value);
		}
		else if (value instanceof String)
		{
			return Check.isEmpty((String)value);
		}
		else
		{
			throw new InvalidAttributeValueException("Value type '" + value.getClass() + "' not supported for " + attribute);
		}
	}

	@Override
	public final Object getEmptyValue()
	{
		final Object value = getValue();

		if (value == null)
		{
			return null;
		}
		if (value instanceof BigDecimal)
		{
			return BigDecimal.ZERO;
		}
		else if (value instanceof String)
		{
			return null;
		}
		else
		{
			throw new InvalidAttributeValueException("Value type '" + value.getClass() + "' not supported for " + attribute);
		}
	}

	@Override
	public final void addAttributeValueListener(final IAttributeValueListener listener)
	{
		listeners.addAttributeValueListener(listener);
	}

	@Override
	public final void removeAttributeValueListener(final IAttributeValueListener listener)
	{
		listeners.removeAttributeValueListener(listener);
	}

	@Override
	public I_C_UOM getC_UOM()
	{
		final I_C_UOM uom = attribute.getC_UOM();
		if (uom == null || uom.getC_UOM_ID() <= 0)
		{
			return null;
		}
		return uom;
	}

	private IAttributeValueCallout _attributeValueCallout = null;

	@Override
	public IAttributeValueCallout getAttributeValueCallout()
	{
		if (_attributeValueCallout == null)
		{
			final IAttributeValueGenerator attributeValueGenerator = getAttributeValueGeneratorOrNull();
			if (attributeValueGenerator instanceof IAttributeValueCallout)
			{
				_attributeValueCallout = (IAttributeValueCallout)attributeValueGenerator;
			}
			else
			{
				_attributeValueCallout = NullAttributeValueCallout.instance;
			}
		}

		return _attributeValueCallout;
	}

	@Override
	public IAttributeValueGenerator getAttributeValueGeneratorOrNull()
	{
		final I_M_Attribute attribute = getM_Attribute();
		final IAttributeValueGenerator attributeValueGenerator = Services.get(IAttributesBL.class).getAttributeValueGeneratorOrNull(attribute);
		return attributeValueGenerator;
	}

	/**
	 * @return true if NOT disposed
	 * @see IAttributeStorage#assertNotDisposed()
	 */
	protected final boolean assertNotDisposed()
	{
		return getAttributeStorage().assertNotDisposed();
	}
}
