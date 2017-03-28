# KDW-tree

## Description

The KDW-tree (short for k-dimensional wavelet tree) is a data structure that supports orthogonal range counting & sampling with a better query time complexity than the KD-tree and the R-tree. This library provides a Java implementation of the KDW-tree.

When you give a (hyper)rectangle as a query, the KDW-tree returns the number of points (*counting*), randomly sampled points (*sampling*) or all points (*reporting*) in the given (hyper)rectangle.

For counting, the KDW-tree's worst-case query time complexity is O(n^(d-2/d) log(n)). This is better than the KD-tree and R-tree's complexity O(n^(d-1/d)). Thus, the KDW-tree can count points faster than the KD-tree if the (hyper)rectangle contains numerous points.

The KDW-tree is a multi-dimensional extension based on the wavelet tree, a data structure that supports efficient two-dimensional range counting. The KDW-tree was proposed in the following paper:   
Y. Okajima and K. Maruyama, Faster Linear-space Orthogonal Range Searching in Arbitrary Dimensions. 2015. In *2015 Proceedings of the Seventeenth Workshop on Algorithm Engineering and Experiments (ALENEX)* , pp. 82-93, DOI: [http://dx.doi.org/10.1137/1.9781611973754.8](http://dx.doi.org/10.1137/1.9781611973754.8)

## Building from source

Clone from git.

    git clone https://github.com/nec-solutioninnovators-ilab/kdw-tree.git

Build with Maven.

    cd kdw-tree
    mvn clean
    mvn package

The kdw-tree JAR file will be generated in the path below:

    kdw-tree/target/kdw-tree-0.0.1-SNAPSHOT.jar

Include the generated JAR file in your classpath.

## Examples

    // import library
    import com.necsoft.vtc.kdwtree.*;
    
    // 2-dimensional data points (20-elements).
    double[][] data = new double[][] {
        {0,3},{1,3},{2,3},{3,3},{4,3},
        {0,2},{1,2},{2,2},{3,2},{4,2},
        {0,1},{1,1},{2,1},{3,1},{4,1},
        {0,0},{1,0},{2,0},{3,0},{4,0},
    };
    
    // creates KDWTree
    KDWTree tree = new ZOrderKDWTree(data);
    
    // counting query. returns number of points in query (hyper)rectangle.
    int count = tree.count(new double[]{1,1}, new double[]{2,2});
    // result is 4
    System.out.println(count);
    
    // reporting query. finds all points in query (hyper)rectangle
    // and returns their indexes in the original data array.
    // This query finds 4 points and returns their indexes: (6, 7, 11, 12).
    int[] reportIndexes = tree.report(new double[]{1,1}, new double[]{2,2});
    for (int index : reportIndexes) {
        System.out.println(index);
    }
    
    // sampling query. finds all points in query (hyper)rectangle, selects points
    // from them at random and returns their indexes in the original data array.
    // This query samples three points from the points in the query.
    java.util.Random rnd = new java.util.Random();
    int[] sampleIndexes = tree.sample(new double[]{1,1}, new double[]{2,2}, 3, rnd);
    for (int index : sampleIndexes) {
        System.out.println(index);
    }

See the Javadoc comments for more information about classes and methods.

## Interfaces and classes

`KDWTree` is the interface of the KDW-tree and has two implementation classes. The first implementation class is `ZOrderKDWTree` that is based on z-order and described in the above paper as the main topic. The second one is `ExternalizedKDWTree` that uses an external tree structure instead of z-order. It is described in Section 2.9 of the paper. These two implementations have the same time complexity and you can choose one of them. Finally, this library also includes `KDTree` class, the traditional kd-tree that can be used for comparison.

## Tips

The KDW-tree works better than the KD-tree only when the query rectangle contains numerous points. Thus, The KDW-tree should be used for a large dataset that contains more than one million points.

Reporting queries of the KDW-tree are not faster than that of the KD-tree because reporting numerous points takes a long time. We recommend a sampling query instead of a reporting query. A sampling query returns randomly sampled points and works efficient even when numerous points are contained in the query.

## License

This software is released under the MIT License, see `LICENSE`.

