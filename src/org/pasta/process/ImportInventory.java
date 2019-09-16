/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2007 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY, without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program, if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.pasta.process;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.util.ProcessUtil;
import org.apache.activemq.util.ByteArrayInputStream;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.compiere.apps.ProcessCtl;
import org.compiere.model.MDocType;
import org.compiere.model.MImage;
import org.compiere.model.MInventory;
import org.compiere.model.MPInstance;
import org.compiere.model.MPInstancePara;
import org.compiere.model.MPriceList;
import org.compiere.model.MProcess;
import org.compiere.model.MProduct;
import org.compiere.model.MStorage;
import org.compiere.model.X_I_Inventory;
import org.compiere.model.X_I_PriceList;
import org.compiere.process.DocAction;
import org.compiere.process.ProcessInfo;
import org.compiere.process.ProcessInfoLog;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.TimeUtil;
import org.compiere.util.Trx;

/**
 * @function std-process
 * @package org.pasta.process
 * @classname ImportPrice
 * @author Pasuwat Wang (CENS ONLINE SERVICES)
 * @created Dec 21, 2018 1:45:15 PM
 */
public class ImportInventory extends SvrProcess {
	
	int AD_Client_ID = 0;
	int AD_Org_ID = 0;
	Timestamp MovementDate ;
	int C_DocType_ID = 0;
	int M_Warehouse_ID = 0;
	int M_Locator_ID = 0;
	int AD_Image_Id = 0;

	protected void prepare() {
		// TODO Auto-generated method stub
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++)
		{
			String name = para[i].getParameterName();
			if (para[i].getParameter() == null)
				;
			else if (name.equals("AD_Org_ID")) {
				AD_Org_ID = para[i].getParameterAsInt();
			}
			else if (name.equals("DataFile")) {
				AD_Image_Id = para[i].getParameterAsInt();
			}
			else if (name.equals("AD_Client_ID")) {
				AD_Client_ID = para[i].getParameterAsInt();
			}
			else if (name.equals("C_DocType_ID")) {
				C_DocType_ID = para[i].getParameterAsInt();
			}
			else if (name.equals("M_Warehouse_ID")) {
				M_Warehouse_ID = para[i].getParameterAsInt();
			}
			else if (name.equals("M_Locator_ID")) {
				M_Locator_ID = para[i].getParameterAsInt();
			}
			else if (name.equals("MovementDate")) {
				MovementDate = (Timestamp)para[i].getParameter();
			}
			else
				log.log(Level.SEVERE, "Unknown Parameter: " + name);
		}
	}

	protected String doIt() throws Exception {
		// TODO Auto-generated method stub
		MImage img = MImage.get(getCtx(), AD_Image_Id);
		readXLSFile(img.getBinaryData());
		
		String result = processImport();
		
		int M_Inventory_ID = getM_Inventorty_ID();
		
		MInventory inventory = MInventory.get(getCtx(), M_Inventory_ID);
		inventory.setC_DocType_ID(getOrgDocType(inventory.getAD_Org_ID()));
		inventory.processIt(DocAction.ACTION_Complete);
		inventory.saveEx();
		
		return result;
	}
	
	private int getOrgDocType(int AD_Org_ID) {
		MDocType types[] = MDocType.getOfDocBaseType(getCtx(), MDocType.DOCBASETYPE_MaterialPhysicalInventory);
		
		for(MDocType type : types) {
			if(type.getAD_Org_ID() == AD_Org_ID)
				return type.getC_DocType_ID();
		}
		
		if(types == null || types.length == 0)
			return 0 ;
		else
			return types[0].getC_DocType_ID();	
	}
	

	private int getM_Inventorty_ID() {
		// TODO Auto-generated method stub
		String sql = "SELECT DISTINCT M_Inventory_ID FROM I_Inventory WHERE M_Inventory_ID IS NOT NULL ";
		return DB.getSQLValue(get_TrxName(), sql);
	}

	public void readXLSFile(byte[] data) throws IOException
	{
		InputStream ExcelFileToRead = new ByteArrayInputStream(data) ;
		HSSFWorkbook wb = new HSSFWorkbook(ExcelFileToRead);

		HSSFSheet sheet=wb.getSheetAt(0);
		HSSFRow row; 
		HSSFCell cell;

		Iterator rows = sheet.rowIterator();
		
		int no_updated_product = -1;

		while (rows.hasNext())
		{
			no_updated_product++;
			if(no_updated_product > 0) { // Skip First Row
				row=(HSSFRow) rows.next();
				Iterator cells = row.cellIterator();
				
				int column_idx = 0; // 1 Product Code 2 List Price 3 Standard Price 4 Limit Price
				
				X_I_Inventory i_inventory = new X_I_Inventory( getCtx() , 0 , get_TrxName());
				i_inventory.setAD_Org_ID(AD_Org_ID);
				i_inventory.setM_Warehouse_ID(M_Warehouse_ID);
				i_inventory.setM_Locator_ID(M_Locator_ID);
				i_inventory.setMovementDate(MovementDate);
				
				while (cells.hasNext())
				{
					cell=(HSSFCell) cells.next();
					
					column_idx++;
					
					if(column_idx == 1)
						i_inventory.setValue(cell.getStringCellValue());
					else if(column_idx == 2)
						i_inventory.setQtyCount(BigDecimal.valueOf(cell.getNumericCellValue()));
					
					i_inventory.setQtyBook(getOnHandQty(i_inventory.getValue() ,  M_Locator_ID));
				}
				
				if(!i_inventory.save(get_TrxName())) {
					throw new AdempiereException("Cannot import data");
				}
			}
			else {
				rows.next();
				DB.executeUpdate("DELETE FROM I_Inventory", this.get_TrxName());
			}
		}
	}
	
	String info;
	
	private BigDecimal getOnHandQty(String productCode , int M_Locator_ID) {
		String sql = "SELECT SUM(QtyOnHand) FROM M_Storage st INNER JOIN M_Product pd ON st.M_Product_ID = pd.M_Product_ID "
				+ "WHERE pd.VALUE =?"	//	1
				+ " AND st.M_Locator_ID=?";	//	2
		return DB.getSQLValueBD(get_TrxName(), sql, productCode , M_Locator_ID);
	}
	
	private String processImport() {
		int AD_Process_ID = 219;
		
		MPInstance instance = new MPInstance(Env.getCtx(), AD_Process_ID, 0);
		if (!instance.save())
		{
			info = Msg.getMsg(Env.getCtx(), "ProcessNoInstance");
			return info;
		}
		
		MProcess process = MProcess.get(getCtx(), AD_Process_ID);
		
		ProcessInfo pi = new ProcessInfo ("", AD_Process_ID);
		pi.setAD_PInstance_ID (instance.getAD_PInstance_ID());
		pi.setClassName(process.getClassname());
		
		int param_idx = 10;
		MPInstancePara para = new MPInstancePara(instance, param_idx);
		para.setParameter("AD_Client_ID", AD_Client_ID);
		if (!para.save())
		{
			String msg = "No AD_Client_ID Parameter added";  //  not translated
			info = msg;
			log.log(Level.SEVERE, msg);
			return info;
		}
		param_idx = param_idx+10;
		
		para = new MPInstancePara(instance, param_idx);
		para.setParameter("AD_Org_ID", AD_Org_ID);
		
		if (!para.save())
		{
			String msg = "No Delete Old Record Parameter added";  //  not translated
			info = msg;
			log.log(Level.SEVERE, msg);
			return info;
		}
		param_idx = param_idx+10;
		
		para = new MPInstancePara(instance, param_idx);
		para.setParameter("M_Locator_ID", M_Locator_ID);
		
		if (!para.save())
		{
			String msg = "No Delete Old Record Parameter added";  //  not translated
			info = msg;
			log.log(Level.SEVERE, msg);
			return info;
		}
		param_idx = param_idx+10;
		
		para = new MPInstancePara(instance, param_idx);
		para.setParameter("MovementDate", MovementDate);
		
		if (!para.save())
		{
			String msg = "No IsImportPriceList Parameter added";  //  not translated
			info = msg;
			log.log(Level.SEVERE, msg);
			return info;
		}
		param_idx = param_idx+10;
		
		para = new MPInstancePara(instance, param_idx);
		para.setParameter("DeleteOldImported", "Y");
		
		if (!para.save())
		{
			String msg = "No IsImportPriceStd Parameter added";  //  not translated
			info = msg;
			log.log(Level.SEVERE, msg);
			return info;
		}
		param_idx = param_idx+10;
		
		para = new MPInstancePara(instance, param_idx);
		para.setParameter("IsUpdateCosting", "N");
		
		if (!para.save())
		{
			String msg = "No IsImportPriceLimit Parameter added";  //  not translated
			info = msg;
			log.log(Level.SEVERE, msg);
			return info;
		}
		param_idx = param_idx+10;
		
		if(!ProcessUtil.startJavaProcess(getCtx(), pi, Trx.get(get_TrxName(), false))) {
			throw new AdempiereException("Import Process Error");
		}

		
		return "";
	}

}
