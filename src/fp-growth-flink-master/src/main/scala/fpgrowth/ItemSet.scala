
package fpgrowth

import scala.collection.SortedSet

/**
  * The wrapper for an item
 *
  * @param name Name of the item, name should be unique for item
  * @param frequency Frequency of item
  */

class ItemSet(var name: SortedSet[String], var frequency: Int) extends Serializable{

  def this() {
    this(null, 0)
  }

  override def equals(o: Any) = o match  {
    case o: Item => this.name == o.name
    case _ => false
  }

  override def hashCode: Int = name.size.hashCode()

  override def toString: String = {
    "[" + this.name + ", " + this.frequency + "]"
  }
}