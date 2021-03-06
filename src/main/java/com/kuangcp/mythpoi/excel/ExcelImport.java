package com.kuangcp.mythpoi.excel;

import com.kuangcp.mythpoi.excel.base.ExcelTransform;
import com.kuangcp.mythpoi.excel.base.MainConfig;
import com.kuangcp.mythpoi.excel.util.ExcelUtil;
import com.kuangcp.mythpoi.utils.base.ReadAnnotationUtil;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

/**
 * Created by https://github.com/kuangcp on 18-2-23  下午9:52
 * Excel导入的操作
 * 通过Excel中Sheet的名字判断对应实体
 * 通过列的中文名得到实体的属性对象,
 * 通过属性对象进行设置值,将Excel的每一行转换成对象
 * 问题:
 * 一个Sheet对应一个类, 怎么处理多Sheet, 用Map?
 * TODO 为什么使用super关键字 extends怎么回事
 *
 * @author kuangcp
 */
@Slf4j
public class ExcelImport {

  private static HSSFWorkbook wb;
  private static MainConfig mainConfig = MainConfig.getInstance();

  /**
   * 根据Excel文件 将Excel转换成对象集合, 只读第一个Sheet
   *
   * @param filePath Excel文件绝对路径
   * @param target 对象集合
   * @return List集合, 否则返回Null
   */
  public static <T extends ExcelTransform> List<T> importExcel(String filePath, Class<T> target) {
    return importExcel(filePath, target, 0);
  }

  /**
   * 根据Excel文件输入流 将Excel转换成对象集合,只读第一个Sheet
   *
   * @param input InputStream 输入流
   * @param target 实体类
   * @return List集合, 或者Null
   */
  public static <T extends ExcelTransform> List<T> importExcel(InputStream input, Class<T> target) {
    return importExcel(input, target, 0);
  }

  /**
   * 根据Excel文件 将Excel转换成对象集合
   *
   * @param filePath Exzcel文件绝对路径
   * @param target 对象集合
   * @param sheetNum Sheet标号 0开始
   * @return List集合, 否则返回Null
   */
  public static <T extends ExcelTransform> List<T> importExcel(String filePath, Class<T> target,
      int sheetNum) {
    FileInputStream inputStream;
    try {
      inputStream = new FileInputStream(filePath);
      return importExcel(inputStream, target, sheetNum);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * 根据Excel文件输入流 将Excel转换成对象集合
   *
   * @param input InputStream 输入流
   * @param target 实体类
   * @param sheetNum Sheet标号 0开始
   * @return List集合, 或者Null
   */
  private static <T extends ExcelTransform> List<T> importExcel(InputStream input, Class<T> target,
      int sheetNum) {
    try {
      POIFSFileSystem fs = new POIFSFileSystem(input);
      wb = new HSSFWorkbook(fs);
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        input.close();
      } catch (IOException e) {
        log.error("file import failed : error", e);
      }
    }
    return readExcelSheetContent(sheetNum, target);
  }

//  /**
//   * TODO 根据标题一一对应,读取多sheet
//   */
//  private static void readTitle(Sheet sheet) {
//
//  }

  /**
   * 根据sheetNum读取数据
   *
   * @param sheetNum sheet标号 0开始
   * @return 数据集合对象
   */
  private static <T extends ExcelTransform> List<T> readExcelSheetContent(int sheetNum,
      Class<T> target) {
    List<T> result = new ArrayList<>();

    if (Objects.isNull(mainConfig)) {
      return result;
    }
    List<ExcelCellMeta> metaList = ReadAnnotationUtil.getCellMetaData(target);
    HSSFSheet sheet = wb.getSheetAt(sheetNum);
    int rowNum = sheet.getLastRowNum();
    HSSFRow row = sheet.getRow(mainConfig.getTitleLastRowNum());
    int colNum = row.getPhysicalNumberOfCells();

    log.info("read excel : rowNum={}, colNum={}", rowNum, colNum);

    String[] titleList = mapFieldAndTitle(colNum, row, metaList);

    try {
      for (int j = mainConfig.getContentStartNum(); j <= rowNum; j++) {
        T cellValue = readRow(sheet.getRow(j), colNum, target, titleList);
        result.add(cellValue);
      }
    } catch (InstantiationException | IllegalAccessException e) {
      log.error("read excel error", e);
    }
    return result;
  }

  private static <T extends ExcelTransform> T readRow(HSSFRow row, int colNum,
      Class<T> target, String[] titleList) throws IllegalAccessException, InstantiationException {
    T cellValue = target.newInstance();
    List<ExcelCellMeta> metaList = ReadAnnotationUtil.getCellMetaData(target);
    for (int i = 0; i < colNum; i++) {
      Field colField = ExcelUtil.getColFieldByTitle(metaList, titleList[i]);
      colField.setAccessible(true);
      HSSFCell cell = row.getCell(i);
      try {
        ExcelUtil.loadCellValue(cell.getCellTypeEnum(), colField, cellValue, cell);
      } catch (Exception e) {
        log.error("load cell value error", e);
      }
    }
    return cellValue;
  }

  /**
   * 将属性和Excel列标题对应起来,这样的写法就能够保证Excel的列顺序混乱也不影响导入
   */
  private static String[] mapFieldAndTitle(int colNum, HSSFRow row,
      List<ExcelCellMeta> metaList) {
    String[] titleList = new String[colNum];
    for (int i = 0; i < colNum; i++) {
      String temp = row.getCell(i).getStringCellValue();
      for (ExcelCellMeta meta : metaList) {
        if (meta.getTitle().equals(temp)) {
          titleList[i] = temp;
        }
      }
    }
    return titleList;
  }
}
