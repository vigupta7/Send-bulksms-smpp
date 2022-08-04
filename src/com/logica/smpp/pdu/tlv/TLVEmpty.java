/*
 * Copyright (c) 1996-2001
 * Logica Mobile Networks Limited
 * All rights reserved.
 *
 * This software is distributed under Logica Open Source License Version 1.0
 * ("Licence Agreement"). You shall use it and distribute only in accordance
 * with the terms of the License Agreement.
 *
 */
package com.logica.smpp.pdu.tlv;

import com.logica.smpp.util.ByteBuffer;
import com.logica.smpp.util.NotEnoughDataInByteBufferException;
import com.logica.smpp.pdu.ValueNotSetException;
import com.logica.smpp.pdu.tlv.WrongLengthException;

/**
 * @author Logica Mobile Networks SMPP Open Source Team
 * @version 1.0, 11 Jun 2001
 */

public class TLVEmpty extends TLV
{
    private boolean present = false;
    
    public TLVEmpty()
    {
        super(0,0);
    }
    
    public TLVEmpty(short p_tag)
    {
        super(p_tag,0,0);
    }
    
    public TLVEmpty(short p_tag, boolean p_present)
    {
        super(p_tag,0,0);
        present = p_present;
        markValueSet();
    }
    
    public ByteBuffer getValueData()
    {
        // nothing, just present or not
        return null;
    }
    
    public void setValueData(ByteBuffer buffer)
    throws WrongLengthException
    {
        // nothing, just set presence
        checkLength(buffer);
        setValue(true);
    }

    public void setValue(boolean p_present)
    {
        present = p_present;
        markValueSet();
    }

    public boolean getValue()
    throws ValueNotSetException
    {
        if (hasValue()) {
            return present;
        } else {
            throw new ValueNotSetException();
        }
    }

    public String debugString()
    {        
        String dbgs = "(empty: ";
        dbgs += super.debugString();
        dbgs += ") ";
        return dbgs;
    }
   
}