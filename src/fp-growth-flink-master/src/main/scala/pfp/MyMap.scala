package pfp

import fpgrowth.Item
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration

import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks

object MyMap {
    def DelDataMap = new RichMapFunction[(String,ListBuffer[Item]),(String,ListBuffer[String])] {
      private val serialVersionUID = 1L
      private var incTrans: java.util.List[Tuple2[String, ListBuffer[Item]]] = null
      override def open(parameters: Configuration) = {
        super.open(parameters)
        this.incTrans = getRuntimeContext().getBroadcastVariable[Tuple2[String, ListBuffer[Item]]]("inc-input")
      }
      override def map(arg0: (String, ListBuffer[Item])) = {
        var r = ListBuffer.empty[String]
        var flag = 0
        import scala.collection.JavaConversions._
        val loop = new Breaks
        loop.breakable(
          for (incT <- incTrans) {
            if (incT._1.equals(arg0._1) ) {//找到对应的事务
              flag = 1
//              val ini = arg0
//              val inc = incT
//              //AND = inc._2.intersect(ini._2)//求交集,ListBuffer格式
//              val DEL = ini._2.diff(inc._2)//求差集，新增部分
//              for(x <- DEL){
//                r += x.name
//              }
              loop.break()
            }
          }
        )
        if(flag==0){//这是个被删除的事务
        // println("deleted trans")
          val DEL = arg0._2
          for(x<-DEL){
            r += x.name
          }
        }
        r.sortWith(_ > _)
        (arg0._1,r)
      }
    }

    def incMap = new RichMapFunction[(ListBuffer[Item],Int),(ListBuffer[String],Int)] {
      private val serialVersionUID = 1L
      override def map(arg0:(ListBuffer[Item],Int)): (ListBuffer[String],Int) = {
        val tmp = ListBuffer.empty[String]
        for(i <- arg0._1){
          tmp += i.name
        }
        tmp.sortWith(_>_)
        (tmp,arg0._2)
      }
    }
}
