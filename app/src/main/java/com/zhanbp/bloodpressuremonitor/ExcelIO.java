package com.zhanbp.bloodpressuremonitor;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.write.Label;
import jxl.write.WritableCell;
import jxl.write.WritableCellFormat;
import jxl.write.WritableFont;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;

public class ExcelIO {
    public static WritableFont       arial14font   = null;
    public static WritableCellFormat arial14format = null;
    public static WritableFont       arial10font   = null;
    public static WritableCellFormat arial10format = null;
    public static WritableFont       arial12font   = null;
    public static WritableCellFormat arial12format = null;

    public final static String UTF8_ENCODING = "UTF-8";
    public final static String GBK_ENCODING  = "GBK";

    private Context context ;
    public ExcelIO(Context context) {
        // TODO Auto-generated constructor stub
        this.context = context;
    }

    private static void format() {
        try {
            arial14font = new WritableFont(WritableFont.ARIAL, 14,
                    WritableFont.BOLD);
            arial14font.setColour(jxl.format.Colour.LIGHT_BLUE);
            arial14format = new WritableCellFormat(arial14font);
            arial14format.setAlignment(jxl.format.Alignment.CENTRE);
            arial14format.setBorder(jxl.format.Border.ALL,
                    jxl.format.BorderLineStyle.THIN);
            arial14format.setBackground(jxl.format.Colour.VERY_LIGHT_YELLOW);

            arial10font = new WritableFont(WritableFont.ARIAL, 10,
                    WritableFont.BOLD);
            arial10format = new WritableCellFormat(arial10font);
            arial10format.setAlignment(jxl.format.Alignment.CENTRE);
            arial10format.setBorder(jxl.format.Border.ALL,
                    jxl.format.BorderLineStyle.THIN);
            arial10format.setBackground(jxl.format.Colour.LIGHT_BLUE);

            arial12font = new WritableFont(WritableFont.ARIAL, 12);
            arial12format = new WritableCellFormat(arial12font);
            arial12format.setBorder(jxl.format.Border.ALL,
                    jxl.format.BorderLineStyle.THIN);
        } catch (WriteException e) {

            e.printStackTrace();
        }
    }

    public static void initExcel(File file, String fileName, String[] colName0) {
        format();
        WritableWorkbook workbook = null;
        try {
            workbook = Workbook.createWorkbook(file);
            WritableSheet sheet0 = workbook.createSheet("BloodPressureData", 0);
            sheet0.addCell((WritableCell) new Label(0, 0, fileName, arial14format));
            for (int col = 0; col < colName0.length; col++) {
                sheet0.addCell(new Label(col, 0, colName0[col], arial10format));
            }
            workbook.write();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }


    public static void initExcel(File file, String fileName, String[] colName0, String[] colName1, String[] colName2) {
        format();
        WritableWorkbook workbook = null;
        try {
            workbook = Workbook.createWorkbook(file);
            WritableSheet sheet0 = workbook.createSheet("BloodPressureData", 0);
            sheet0.addCell((WritableCell) new Label(0, 0, fileName, arial14format));
            for (int col = 0; col < colName0.length; col++) {
                sheet0.addCell(new Label(col, 0, colName0[col], arial10format));
            }
            WritableSheet sheet1 = workbook.createSheet("GasPressureData", 1);
            sheet1.addCell((WritableCell) new Label(0, 0, fileName, arial14format));
            for (int col = 0; col < colName1.length; col++) {
                sheet1.addCell(new Label(col, 0, colName1[col], arial10format));
            }
            WritableSheet sheet2 = workbook.createSheet("CuffPressureData", 2);
            sheet2.addCell((WritableCell) new Label(0, 0, fileName, arial14format));
            for (int col = 0; col < colName2.length; col++) {
                sheet1.addCell(new Label(col, 0, colName1[col], arial10format));
            }
            workbook.write();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void writeObjListToExcel(ArrayList<ArrayList<Long>> objList,
                                               String fileName, Context c, int sheetNum) {
        if (objList != null ) {
            WritableWorkbook writebook = null;
            InputStream in = null;
            try {
                WorkbookSettings setEncode = new WorkbookSettings();
                setEncode.setEncoding(UTF8_ENCODING);
                in = new FileInputStream(new File(fileName));
                Workbook workbook = Workbook.getWorkbook(in);
                writebook = Workbook.createWorkbook(new File(fileName),
                        workbook);
                WritableSheet sheet = writebook.getSheet(sheetNum);
                for (int i = 0; i < objList.size(); i++) {                                   //j行 i列
                    ArrayList<Long> list = objList.get(i);
                    for (int j = 0; j < list.size(); j++) {
                        sheet.addCell(new jxl.write.Number(j,i + 1 , list.get(j), arial12format));
                    }
                }
                writebook.write();
                Toast.makeText(c, "保存成功", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (writebook != null) {
                    try {
                        writebook.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    }


    public int[][] getXlsData(String paramString, int paramInt)
    {
        int[][] pressure=new int[2][9100];
        AssetManager localAssetManager = context.getAssets();
        try
        {
            Workbook localWorkbook = Workbook.getWorkbook(localAssetManager.open(paramString));
            Sheet localSheet = localWorkbook.getSheet(paramInt);
            int i = localWorkbook.getNumberOfSheets();
            int j = localSheet.getRows();
            int k = localSheet.getColumns();
            Log.d("Excel", "total cols is 列=" + k);
            for (int m = 0; m < 9100; m++)
            {
                pressure[0][m] = Double.valueOf(localSheet.getCell(4, m+1).getContents()).intValue();
                pressure[1][m] = Double.valueOf(localSheet.getCell(3, m+1).getContents()).intValue();
            }
            localWorkbook.close();
            Log.i("Excel", "read out successfully" );

            return pressure;
        }
        catch (Exception localException)
        {
            Log.e("Excel", "read out error =" + localException, localException);
        }

        return null;
    }

    public static Object getValueByRef(Class cls, String fieldName) {//这啥？？？？？？？？？？？
        Object value = null;
        fieldName = fieldName.replaceFirst(fieldName.substring(0, 1), fieldName
                .substring(0, 1).toUpperCase());
        String getMethodName = "get" + fieldName;
        try {
            Method method = cls.getMethod(getMethodName);
            value = method.invoke(cls);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }
}
