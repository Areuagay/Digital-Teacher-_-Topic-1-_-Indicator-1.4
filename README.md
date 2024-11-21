
#Incremental Update Model for Frequent Itemsets Mining 
DIFIM with FP-Growth in Apache Flink.
The parallel FP-Growth has been implemented in Apache Flink by [https://github.com/truongnx15/fp-growth-flink](https://github.com/truongnx15/fp-growth-flink). Our experiments on FP-Growth algorithm combined with DIFIM are carried out based on it. 
The program read transaction database before and after the update from two input files. Each transaction is on one line and items within the transaction are non-empty string(no spaces) seperated by space delimiter. The first number in each line represents the transaction number `tid` of this transaction.
Also, our program read the previous frequent itemsets file from `iniR.txt`. Each frequent itemset is one line, the first number is the occurrence time of the itemset, and the items with the itemset are non-empty,

======

# Requirements
* Flink 1.10.2
* HADOOP_HOME is set

# Build

*  We have build `.jar` package in `.../out/artifacts`.

### Parameters

* --initial: Path to the input file of transaction database before the update
* --inc: Path to the input file of transaction database after the update
* --iniR: Path to the input file of previous mining result of the transaction database before the update.
* --support: min supporta, a real number in the range (0,1]


## Data
Download the [T10I4D100K Dataset](http://fimi.uantwerpen.be/data/)


# Example
* **Distribute version:** 
* --hdfs:`flink run hdfs://syj/jar/Eclat/incFPG.jar --initial 
hdfs://syj/data/sample-ini.txt --inc hdfs://syj/data/sample-inc.txt --iniR hdfs://syj/data/iniR.txt --min-sup 0.5`  
* --Standalone Version:`flink run /home/syj/jar/Eclat/incFPG.jar --initial 
/home/syj/data/sample-ini.txt --inc /home/syj/data/sample-inc.txt --iniR /home/syj/data/iniR.txt --min-sup 0.5`  

Thanks to researchers in data mining field for implementing parallel algorithms on Flink. For more details, please look at package example.



