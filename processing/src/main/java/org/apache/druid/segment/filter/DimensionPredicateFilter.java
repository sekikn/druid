/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.segment.filter;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.druid.query.BitmapResultFactory;
import org.apache.druid.query.extraction.ExtractionFn;
import org.apache.druid.query.filter.BitmapIndexSelector;
import org.apache.druid.query.filter.DruidDoublePredicate;
import org.apache.druid.query.filter.DruidFloatPredicate;
import org.apache.druid.query.filter.DruidLongPredicate;
import org.apache.druid.query.filter.DruidPredicateFactory;
import org.apache.druid.query.filter.Filter;
import org.apache.druid.query.filter.FilterTuning;
import org.apache.druid.query.filter.ValueMatcher;
import org.apache.druid.query.filter.vector.VectorValueMatcher;
import org.apache.druid.query.filter.vector.VectorValueMatcherColumnProcessorFactory;
import org.apache.druid.segment.ColumnSelector;
import org.apache.druid.segment.ColumnSelectorFactory;
import org.apache.druid.segment.DimensionHandlerUtils;
import org.apache.druid.segment.vector.VectorColumnSelectorFactory;

import java.util.Set;

/**
 */
public class DimensionPredicateFilter implements Filter
{
  private final String dimension;
  private final DruidPredicateFactory predicateFactory;
  private final String basePredicateString;
  private final ExtractionFn extractionFn;
  private final FilterTuning filterTuning;

  public DimensionPredicateFilter(
      final String dimension,
      final DruidPredicateFactory predicateFactory,
      final ExtractionFn extractionFn
  )
  {
    this(dimension, predicateFactory, extractionFn, null);
  }

  public DimensionPredicateFilter(
      final String dimension,
      final DruidPredicateFactory predicateFactory,
      final ExtractionFn extractionFn,
      final FilterTuning filterTuning
  )
  {
    Preconditions.checkNotNull(predicateFactory, "predicateFactory");
    this.dimension = Preconditions.checkNotNull(dimension, "dimension");
    this.basePredicateString = predicateFactory.toString();
    this.extractionFn = extractionFn;
    this.filterTuning = filterTuning;

    if (extractionFn == null) {
      this.predicateFactory = predicateFactory;
    } else {
      this.predicateFactory = new DruidPredicateFactory()
      {
        final Predicate<String> baseStringPredicate = predicateFactory.makeStringPredicate();

        @Override
        public Predicate<String> makeStringPredicate()
        {
          return input -> baseStringPredicate.apply(extractionFn.apply(input));
        }

        @Override
        public DruidLongPredicate makeLongPredicate()
        {
          return input -> baseStringPredicate.apply(extractionFn.apply(input));
        }

        @Override
        public DruidFloatPredicate makeFloatPredicate()
        {
          return input -> baseStringPredicate.apply(extractionFn.apply(input));
        }

        @Override
        public DruidDoublePredicate makeDoublePredicate()
        {
          return input -> baseStringPredicate.apply(extractionFn.apply(input));
        }
      };
    }
  }

  @Override
  public <T> T getBitmapResult(BitmapIndexSelector selector, BitmapResultFactory<T> bitmapResultFactory)
  {
    return Filters.matchPredicate(dimension, selector, bitmapResultFactory, predicateFactory.makeStringPredicate());
  }

  @Override
  public ValueMatcher makeMatcher(ColumnSelectorFactory factory)
  {
    return Filters.makeValueMatcher(factory, dimension, predicateFactory);
  }

  @Override
  public VectorValueMatcher makeVectorMatcher(final VectorColumnSelectorFactory factory)
  {
    return DimensionHandlerUtils.makeVectorProcessor(
        dimension,
        VectorValueMatcherColumnProcessorFactory.instance(),
        factory
    ).makeMatcher(predicateFactory);
  }

  @Override
  public boolean canVectorizeMatcher()
  {
    return true;
  }

  @Override
  public Set<String> getRequiredColumns()
  {
    return ImmutableSet.of(dimension);
  }

  @Override
  public boolean supportsBitmapIndex(BitmapIndexSelector selector)
  {
    return selector.getBitmapIndex(dimension) != null;
  }

  @Override
  public boolean shouldUseBitmapIndex(BitmapIndexSelector selector)
  {
    return Filters.shouldUseBitmapIndex(this, selector, filterTuning);
  }

  @Override
  public boolean supportsSelectivityEstimation(ColumnSelector columnSelector, BitmapIndexSelector indexSelector)
  {
    return Filters.supportsSelectivityEstimation(this, dimension, columnSelector, indexSelector);
  }

  @Override
  public double estimateSelectivity(BitmapIndexSelector indexSelector)
  {
    return Filters.estimateSelectivity(
        dimension,
        indexSelector,
        predicateFactory.makeStringPredicate()
    );
  }

  @Override
  public String toString()
  {
    if (extractionFn != null) {
      return StringUtils.format("%s(%s) = %s", extractionFn, dimension, basePredicateString);
    } else {
      return StringUtils.format("%s = %s", dimension, basePredicateString);
    }
  }
}
