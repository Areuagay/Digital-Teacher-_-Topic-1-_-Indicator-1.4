package incFPGrowth

import fpgrowth.Item
import helper.IOHelperFlink
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.configuration.Configuration
import pfp.{MyMap, PFPGrowth, ParallelCounting}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks

object iniRFPG {
  def main(args: Array[String]) {

    println("INCfromFile:  STARTING FPGROWTH IN FLINK")

    //Get global variables for Flink and parameter parser
    val parameter = ParameterTool.fromArgs(args)
    val env = ExecutionEnvironment.getExecutionEnvironment
    val itemDelimiter = " "

    //Parse input parameter
    val input = parameter.get("input")
    val inc = parameter.get("inc")
    val iniR = parameter.get("iniR")
   // println(input)
    val minSupport = parameter.get("support").toDouble
    val numGroup = parameter.get("group")    //group是什么

    //println("input: " + input + " support: " + minSupport + " numGroup: " + numGroup)

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
    import org.apache.flink.api.scala._
    //Run FLink FPGrowth
    val iniTrans = IOHelperFlink.MyreadInput(env, input, itemDelimiter) //input:String格式(文件地址),itemDelimiter为空格
    val incTrans = IOHelperFlink.MyreadInput(env, inc, itemDelimiter) //input:String格式,itemDelimiter初始化为空
    val iniT = iniTrans.groupBy(0).reduceGroup(ParallelCounting.MyGroupReduce)//(string,ListBuffer[Item]格式
    val incT = incTrans.groupBy(0).reduceGroup(ParallelCounting.MyGroupReduce)
    val F = IOHelperFlink.readIniR(env, iniR, " ")

    val incCount = incT.count() * minSupport

    //Preprocess phase
    val addTrans = incT.map(new RichMapFunction[Tuple2[String,ListBuffer[Item]],Tuple2[String,ListBuffer[Item]]]{
      private val serialVersionUID = 1L
      private var iniTrans: java.util.List[Tuple2[String, ListBuffer[Item]]] = null

      override def open(parameters: Configuration) = {
        super.open(parameters)
        this.iniTrans = getRuntimeContext().getBroadcastVariable[Tuple2[String, ListBuffer[Item]]]("ini-input")
      }

      override def map(arg0: Tuple2[String, ListBuffer[Item]]) = {
        var r = ListBuffer.empty[Item]
        var flag = 0
        import scala.collection.JavaConversions._
        val loop = new Breaks
        loop.breakable(
          for (iniT <- iniTrans) {
            if (iniT._1.equals(arg0._1) ) {//找到对应的事务
              flag = 1
//              val inc = arg0
//              val ini = iniT
//              val ADD = inc._2.diff(ini._2)//求差集，新增部分
//              r = ADD
              loop.break()
            }
          }
        )
        if(flag==0){//这是个新增的事务
        val ADD = arg0._2
          r = ADD
        }
        (arg0._1,r)
      }
    })
      .withBroadcastSet(iniTrans,"ini-input").filter(x=>x._2.nonEmpty) //[int1,0][int2,0]格式
    val addData = addTrans.map(x=> x._2)
    val addTcount = (addTrans.count() * minSupport).toLong
    val iniTcount = (iniT.count() * minSupport).toLong

    val delData = iniT.map(MyMap.DelDataMap).withBroadcastSet(incTrans,"inc-input").filter(x=>x._2.nonEmpty)

    // Incremental phase
   val frequentItemsets = pfp.run(addData)
    val f = frequentItemsets.map(MyMap.incMap)


    // Merge phase
    // intersect(F,f)
    val bothmid = frequentItemsets.map(new RichMapFunction[Tuple2[ListBuffer[Item],Int],Tuple2[ListBuffer[String],Int]] {
      private val serialVersionUID = 1L
      private var iniR:java.util.List[Tuple2[ListBuffer[String],Int]] = null

      override def open(parameters: Configuration): Unit = {
        super.open(parameters)
        this.iniR = getRuntimeContext.getBroadcastVariable[Tuple2[ListBuffer[String],Int]]("F")
         // getRuntimeContext().getBroadcastVariable[ListBuffer[Item]]("F")
      }
      override def map(in: Tuple2[ListBuffer[Item],Int])= {
        val in1 = in._1.iterator
        var allItem = mutable.ListBuffer.empty[String]
        while (in1.hasNext){//为了求交集，去掉结果中的出现次数，只根据项集name求交集
          val next = in1.next()
          allItem += next.name
        }
        allItem.sortWith(_ > _)
        import scala.collection.JavaConversions._
        var t = ListBuffer.empty[String]
        var count = 0
        try{
          for(x <- iniR){
//            var tmp = ListBuffer.empty[String]
//            val x1 = x._1.iterator
//            while(x1.hasNext){
//              tmp += x1.next().name
//            }
//            tmp.sortWith(_ > _)
            if(allItem.equals(x._1)){//这个项集同时出现在F和f中
              t = x._1
              count += x._2
            }
          }
        }catch {
          case e: NullPointerException => println("hello")
        }
        (t,in._2+count)
      }
    })
      .withBroadcastSet(F,"F").filter(x=> x._1.nonEmpty)

    val both = bothmid.map(new RichMapFunction[Tuple2[ListBuffer[String],Int],Tuple2[ListBuffer[String],Int]]{
      private val serialVersionUID = 1L
      private var del: java.util.List[(String,ListBuffer[String])]= null

      override def open(parameters: Configuration): Unit = {
        super.open(parameters)
        this.del = getRuntimeContext().getBroadcastVariable("del-trans")
      }
      override def map(in: (ListBuffer[String], Int)) = {
        val tmp = in._1
        var count : Int = in._2
        import scala.collection.JavaConversions._
        try{
          for(x <- this.del){
            if(x._2.containsAll(in._1)){
              count -= 1
            }
          }
        }catch {
          case e: NullPointerException => println("null pointer exception")
        }
        (tmp,count)
      }
    })
        .withBroadcastSet(delData,"del-trans").filter(x=> x._2 >= incCount)


    //diff(F,f)
      val inFmid = F.map(new RichMapFunction[Tuple2[ListBuffer[String],Int],Tuple2[ListBuffer[String],Int]] {
        private val serialVersionUID = 1L
        private var both: java.util.List[(ListBuffer[String],Int)] = null
        override def open(parameters: Configuration): Unit = {
          super.open(parameters)
          this.both = getRuntimeContext().getBroadcastVariable[(ListBuffer[String],Int)]("both")
        }
        override def map(in: (ListBuffer[String], Int)): (ListBuffer[String], Int) = {
          var r = ListBuffer.empty[String]
          var flag = 0
          import scala.collection.JavaConversions._
          val loop = new Breaks
          loop.breakable(
            for (x <- this.both) {
              //System.out.println(in._1+",,,,,"+x._1)
              if (in._1.equals(x._1)) {
                flag = 1
                loop.break()
              }
            }
          )
          if(flag==0)
            (in._1,in._2)
          else
            (r,in._2)
        }
      }).withBroadcastSet(both,"both").filter(x=> x._1.nonEmpty)


    val inF = inFmid.map(
      new RichMapFunction[Tuple2[ListBuffer[String],Int],Tuple2[ListBuffer[String],Int]] {
        private val serialVersionUID = 1L
        private var incT:java.util.List[Tuple2[String,ListBuffer[Item]]] = null
        override def open(parameters: Configuration): Unit = {
          super.open(parameters)
          this.incT = getRuntimeContext().getBroadcastVariable[Tuple2[String,ListBuffer[Item]]]("incT")
        }
        override def map(in: Tuple2[ListBuffer[String],Int])= {
          import scala.collection.JavaConversions._
          var count = 0
          for (x <- this.incT) {
            val tmp = ListBuffer.empty[String]
            for(i <- x._2){
              tmp += i.name
            }
            tmp.sortWith(_>_)
            if (tmp.containsAll(in._1)) {
              count += 1
            }
          }
          (in._1, in._2 + count)
        }
      }
    ).withBroadcastSet(addTrans,"incT").filter(x=> x._2 >= incCount)


    val FGlobal=inF.map(new RichMapFunction[Tuple2[ListBuffer[String],Int],Tuple2[ListBuffer[String],Int]] {
        private val serialVersionUID = 1L
        private var del: java.util.List[(String,ListBuffer[String])]= null
        override def open(parameters: Configuration): Unit = {
          super.open(parameters)
          this.del = getRuntimeContext().getBroadcastVariable("del-trans")
        }
        override def map(in: Tuple2[ListBuffer[String],Int]) = {
          var count = in._2
          import scala.collection.JavaConversions._
          for(x <- this.del){
            if(x._2.containsAll(in._1)){
              count -= 1
            }
          }
          (in._1,count)
        }
      })
      .withBroadcastSet(delData,"del-trans").filter(x=> x._1.nonEmpty && x._2 >= incCount)


    val infmid = frequentItemsets.map(MyMap.incMap)
      .map(new RichMapFunction[Tuple2[ListBuffer[String],Int],Tuple2[ListBuffer[String],Int]] {
      private val serialVersionUID = 1L
      private var both:java.util.List[(ListBuffer[String],Int)] = null
      override def open(parameters: Configuration): Unit = {
        super.open(parameters)
        this.both = getRuntimeContext.getBroadcastVariable[(ListBuffer[String],Int)]("both")
      }
      override def map(in: (ListBuffer[String], Int)): (ListBuffer[String], Int) = {
        var r = ListBuffer.empty[String]
        var flag = 0
        import scala.collection.JavaConversions._
        val loop = new Breaks
        loop.breakable(
          for (x <- this.both) {
            if (in._1.equals(x._1)) {
              flag = 1
              loop.break()
            }
          }
        )
        if(flag==0) //没找到与both一样的，说明只在f中
          (in._1,in._2)
        else
          (r,in._2)
      }
    }).withBroadcastSet(both,"both").filter(x=> x._1.nonEmpty)


    val inf = infmid.map(
      new RichMapFunction[Tuple2[ListBuffer[String],Int],Tuple2[ListBuffer[String],Int]] {
        private val serialVersionUID = 1L
        private var iniT:java.util.List[Tuple2[String,ListBuffer[Item]]] = null
        override def open(parameters: Configuration): Unit = {
          super.open(parameters)
          this.iniT = getRuntimeContext().getBroadcastVariable[Tuple2[String,ListBuffer[Item]]]("iniT")
        }
        override def map(in: Tuple2[ListBuffer[String],Int])= {
          import scala.collection.JavaConversions._
          var count = 0
          for (x <- this.iniT) {
            var tmp = ListBuffer.empty[String]
            for(i <- x._2){
              tmp += i.name
            }
            tmp.sortWith(_ > _)
            if (tmp.containsAll(in._1)) {
              count += 1
            }
          }
          (in._1, in._2 + count)
        }
      }).withBroadcastSet(iniT,"iniT").filter(x=> x._2 >= incCount)

    val fGlobal = inf.map(new RichMapFunction[Tuple2[ListBuffer[String],Int],Tuple2[ListBuffer[String],Int]] {
          private val serialVersionUID = 1L
          private var del: java.util.List[(String,ListBuffer[String])]= null
          override def open(parameters: Configuration): Unit = {
            super.open(parameters)
            this.del = getRuntimeContext().getBroadcastVariable("del-trans")
          }
          override def map(in: Tuple2[ListBuffer[String],Int]) = {
            var count = in._2
            import scala.collection.JavaConversions._
            for(x <- this.del){
              if(x._2.containsAll(in._1)){
                count -= 1
              }
            }
            (in._1,count)
          }
        }).withBroadcastSet(delData,"del-trans").filter(x=> x._1.nonEmpty && x._2 >= incCount)

    both.print()
    FGlobal.print()
    fGlobal.print()


    println("TIME: " + (System.currentTimeMillis() - starTime) / 1000.0)
  }
}
