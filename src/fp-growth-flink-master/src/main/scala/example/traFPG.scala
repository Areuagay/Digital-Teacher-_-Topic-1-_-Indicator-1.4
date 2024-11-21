package incFPGrowth

import helper.IOHelperFlink
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.api.scala.ExecutionEnvironment
import pfp.{MyMap, PFPGrowth, ParallelCounting}

object traFPG{
  def main(args: Array[String]) {

    println("iniRempty:  STARTING FPGROWTH IN FLINK")

    //Get global variables for Flink and parameter parser
    val parameter = ParameterTool.fromArgs(args)
    val env = ExecutionEnvironment.getExecutionEnvironment
    val itemDelimiter = " "

    //Parse input parameter
    val input = parameter.get("input")

    val minSupport = parameter.get("support")
    val numGroup = parameter.get("group")    //group是什么

    //input and support are required
    if (input == null || input == "" || minSupport == null) {
      println("Please indicate input file and support: --input inputFile --support minSupport")
      return
    }

    //For measuring running time
    val starTime = System.currentTimeMillis()
    val pfp = new PFPGrowth(env, minSupport.toDouble)

    //Set number of group
    if (numGroup != null && numGroup.toInt >=0 ) {
      pfp.numGroup = numGroup.toInt
    }

    //Run FLink FPGrowth
    import org.apache.flink.api.scala._
    val Trans = IOHelperFlink.MyreadInput(env, input, itemDelimiter)
    val T = Trans.groupBy(0).reduceGroup(ParallelCounting.MyGroupReduce)
      .map(x=> x._2)
    //System.out.println(T.count())

    val frequentItemsets = pfp.run(T)//data:DataSet[ListBuffer[Item]
        .map(MyMap.incMap)
    frequentItemsets.print()

    println("TIME: " + (System.currentTimeMillis() - starTime) / 1000.0)
  }
}
