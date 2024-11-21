package helper

import fpgrowth.Item
import org.apache.flink.api.common.functions.{FlatMapFunction, RichFlatMapFunction}
import org.apache.flink.api.scala.{DataSet, ExecutionEnvironment, _}
import org.apache.flink.util.Collector

import scala.collection.mutable.ListBuffer

/**
  * This is a helper to read transaction from input file for flink
  */

object IOHelperFlink {
  /**
    * Read transactions in text file for Flink
    *
    * @param env The Flink runtime environment
    * @param input The path to input file
    * @param itemDelimiter The delimiter of items within one transaction
    * @return DataSet of transactions(ListBuffer[Item])
    */
  def MyreadInput(env: ExecutionEnvironment, input: String, itemDelimiter: String): DataSet[Tuple2[String,ListBuffer[Item]]] = {
    //Read dataset
    env.readTextFile(input)
      .flatMap(new FlatMapFunction[String, Tuple2[String,ListBuffer[Item]]] {
        override def flatMap(line: String, out: Collector[Tuple2[String,ListBuffer[Item]]]): Unit = {
          val itemset = ListBuffer.empty[Item]
          //Split line to get items
          val items = line.trim.split(itemDelimiter)//删除字符串的头尾空白符、以itemDelimiter做分割
          //val count = items(0).toInt
         // var result = new Tuple2(items(0),itemset)//items第一个序号表示事务号

          if (items.nonEmpty) {
            items.foreach { x =>
              if (x.length() > 0) itemset += new Item(x, 0)//初始化itemset
            }
            itemset.remove(0)
            //itemset.remove(0)//去掉tid的那一个
            out.collect((items(0),itemset))//每行一组
          }
        }
      })
  }

  def readIniR(env: ExecutionEnvironment, input: String, itemDelimiter: String): DataSet[Tuple2[ListBuffer[String],Int]] ={
    env.readTextFile(input).flatMap(new RichFlatMapFunction[String,Tuple2[ListBuffer[String],Int]] {
      override def flatMap(line: String, out: Collector[Tuple2[ListBuffer[String],Int]]): Unit = {
        val itemset = ListBuffer.empty[String]
        if(line.length>1){
          val items = line.trim.split(itemDelimiter)//删除字符串的头尾空白符、以itemDelimiter做分割
          if (items.nonEmpty) {
            for(x <- items){
              //System.out.print(x+"---")
              if(x.length>0)
                itemset += x
            }
            val count = itemset(0).toInt
            itemset.remove(0)
            out.collect((itemset,count))//每行一组
          }
        }
      }
    })
  }

//  def readIniR(env: ExecutionEnvironment, input: String, itemDelimiter: String): DataSet[Tuple2[ListBuffer[Item],Int]] ={
//    env.readTextFile(input).flatMap(new RichFlatMapFunction[String,Tuple2[ListBuffer[Item],Int]] {
//      override def flatMap(line: String, out: Collector[Tuple2[ListBuffer[Item],Int]]): Unit = {
//        val itemset = ListBuffer.empty[Item]
//        if(line.length>1){
//          val items = line.trim.split(itemDelimiter)//删除字符串的头尾空白符、以itemDelimiter做分割
//          if (items.nonEmpty) {
//            for(x <- items){
//              itemset += new Item(x,items(0).toInt)
//            }
//            val count = items(0).toInt
//            itemset.remove(0)
//            out.collect((itemset,count))//每行一组
//          }
//        }
//      }
//    })
//  }

  def readInput(env: ExecutionEnvironment, input: String, itemDelimiter: String): DataSet[ListBuffer[Item]] = {
    //Read dataset
    env.readTextFile(input)
      .flatMap(new FlatMapFunction[String, ListBuffer[Item]] {
        override def flatMap(line: String, out: Collector[ListBuffer[Item]]): Unit = {

          val itemset = ListBuffer.empty[Item]
          //Split line to get items
          val items = line.trim.split(itemDelimiter)//删除字符串的头尾空白符、以itemDelimiter做分割
          //val tid = items.take(0)
          if (items.nonEmpty) {
            items.foreach { x =>
              if (x.length() > 0) itemset += new Item(x, 0)//初始化itemset
            }
            itemset.remove(0)//去掉tid的那一个
            out.collect(itemset)//每行一组
          }
        }
      })
  }
}
