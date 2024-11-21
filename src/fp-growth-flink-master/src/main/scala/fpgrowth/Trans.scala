package fpgrowth

import scala.collection.mutable.ListBuffer


/**
  * The wrapper for a transaction
 *
  * @param tid Number of the item, name should be unique for item
  * @param frequency Frequency of item
  */

class Trans(var tid: String, var mark: Int, var items: ListBuffer[Item]) extends Serializable with Ordered[Trans]{

  def this() {
    this(null, 0, null)
  }
  def getTid(): String ={
    this.tid
  }
  def getMark(): Int = {
    this.mark
  }
  def getItems(): ListBuffer[Item] = {
    this.items
  }

  override def equals(o: Any) = o match  {
    case o: Trans => this.tid == o.tid
    case _ => false
  }

  override def hashCode: Int = tid.length.hashCode()


  override def compare(o: Trans): Int = {
    this.tid compare o.tid
  }

//  override def toString: String = {
//    "[" + this.tid + ", " + this.items.toList + "]"
//  }
}
