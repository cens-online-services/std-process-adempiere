package org.pasta.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.apache.commons.lang.StringUtils;
import org.compiere.acct.Doc;
import org.compiere.acct.DocLine;
import org.compiere.acct.Fact;
import org.compiere.acct.FactLine;
import org.compiere.model.FactsValidator;
import org.compiere.model.MAccount;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MAllocationHdr;
import org.compiere.model.MAllocationLine;
import org.compiere.model.MClient;
import org.compiere.model.MDocType;
import org.compiere.model.MInventory;
import org.compiere.model.MInvoice;
import org.compiere.model.MPayment;
import org.compiere.model.MWithholding;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.model.X_C_BP_Withholding;
import org.compiere.model.X_C_Withholding_Acct;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Msg;

public class StdProcessValidation implements ModelValidator {
	/** Logger */
	private static CLogger log = CLogger.getCLogger(StdProcessValidation.class);
	
	/** Client */
	private int m_AD_Client_ID = -1;
	/** User */
	private int m_AD_User_ID = -1;
	
	/** Role */
	private int m_AD_Role_ID = -1;

	/** Organization **/
	private int m_AD_Org_ID = -1;

	public int getAD_Client_ID() {
		return m_AD_Client_ID;
	}
	
	private Properties m_ctx;
	
	private String trxName = "";
	
	
	List<String> docValidationL ;
	
	List<String> modelValidationL ;

	public void initialize(ModelValidationEngine engine, MClient client) {
		// client = null for global validator
		if (client != null) {
			m_AD_Client_ID = client.getAD_Client_ID();
			log.info(client.toString());
		} else {
			log.info("Initializing Validator: " + this.toString());
		}
		
		addModelValidation(engine);
	}

	private void addModelValidation(ModelValidationEngine engine) {
		// TODO Auto-generated method stub
		
		engine.addModelChange(MInventory.Table_Name, this);
		
	}

	public String login(int AD_Org_ID, int AD_Role_ID, int AD_User_ID) {
		m_AD_User_ID = AD_User_ID;
		m_AD_Role_ID = AD_Role_ID;
		m_AD_Org_ID = AD_Org_ID;
		
		return null;
	}

	public String modelChange(PO po, int type) throws Exception {	
		if(po == null)
			return null;
		
		
		
		if(MInventory.Table_Name.equals(po.get_TableName()) && (type == TYPE_BEFORE_NEW || type == TYPE_BEFORE_CHANGE) ) 
		{
			MInventory inventory = (MInventory)po;
			int C_DocType_ID = getMMIDocForOrg(inventory.getCtx() , inventory.getAD_Org_ID() , inventory.get_TrxName());
			if(C_DocType_ID > 0)
				inventory.setC_DocType_ID(C_DocType_ID);
		}
		
		return "";
	}
	
	private int getMMIDocForOrg(Properties ctx , int AD_Org_ID , String trxName ) {
		String whereClause = "AD_Org_ID = ? AND DocBaseType = 'MMI' ";
		
		return new Query(ctx , MDocType.Table_Name , whereClause , trxName)
				.setParameters(AD_Org_ID)
				.firstId();
	}

	public String docValidate(PO po, int timing) {
		// TODO Auto-generated method stub
		if(po == null)
			return null;
		

		return "";
	}

}
