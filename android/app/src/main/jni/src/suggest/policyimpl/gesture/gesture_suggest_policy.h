/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef LATINIME_GESTURE_SUGGEST_POLICY_H
#define LATINIME_GESTURE_SUGGEST_POLICY_H

#include <algorithm>
#include <cmath>

#include "defines.h"
#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/dicnode/dic_node_utils.h"
#include "suggest/core/dicnode/dic_node_vector.h"
#include "suggest/core/dictionary/dictionary.h"
#include "suggest/core/dictionary/error_type_utils.h"
#include "suggest/core/layout/proximity_info.h"
#include "suggest/core/layout/proximity_info_state.h"
#include "suggest/core/layout/proximity_info_utils.h"
#include "suggest/core/policy/scoring.h"
#include "suggest/core/policy/suggest_policy.h"
#include "suggest/core/policy/traversal.h"
#include "suggest/core/policy/weighting.h"
#include "suggest/core/result/suggestion_results.h"
#include "suggest/core/session/dic_traverse_session.h"
#include "suggest/core/suggest_options.h"
#include "suggest/policyimpl/typing/scoring_params.h"
#include "utils/char_utils.h"

namespace latinime {

class GestureTraversal : public Traversal {
 public:
    static const GestureTraversal *getInstance() { return &sInstance; }

    AK_FORCE_INLINE int getMaxPointerCount() const {
        return MAX_POINTER_COUNT_G;
    }

    AK_FORCE_INLINE bool allowsErrorCorrections(const DicNode *const dicNode) const {
        return dicNode->getNormalizedSpatialDistance() < 1.85f;
    }

    AK_FORCE_INLINE bool isOmission(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode, const DicNode *const childDicNode,
            const bool allowsErrorCorrections) const {
        if (childDicNode->canBeIntentionalOmission()) {
            return true;
        }
        if (!allowsErrorCorrections) {
            return false;
        }
        const int inputSize = traverseSession->getInputSize();
        const int point0Index = dicNode->getInputIndex(0);
        // Never omit the very first letter of a gesture stroke
        if (point0Index <= 0 || dicNode->isRoot() || dicNode->isCompletion(inputSize)) {
            return false;
        }
        const ProximityInfoState *const pInfoState = traverseSession->getProximityInfoState(0);
        const int keyId = traverseSession->getProximityInfo()->getKeyIndexOf(
                CharUtils::toBaseLowerCase(childDicNode->getNodeCodePoint()));
        if (keyId == NOT_AN_INDEX) {
            return false;
        }
        // Only consider omission if this character lies along the upcoming sampled gesture path
        return pInfoState->isKeyInSerchKeysAfterIndex(point0Index, keyId);
    }

    AK_FORCE_INLINE bool isSpaceSubstitutionTerminal(
            const DicTraverseSession *const traverseSession, const DicNode *const dicNode) const {
        return false;
    }

    AK_FORCE_INLINE bool isSpaceOmissionTerminal(
            const DicTraverseSession *const traverseSession, const DicNode *const dicNode) const {
        return false;
    }

    AK_FORCE_INLINE bool shouldDepthLevelCache(
            const DicTraverseSession *const traverseSession) const {
        return false;
    }

    AK_FORCE_INLINE bool shouldNodeLevelCache(
            const DicTraverseSession *const traverseSession, const DicNode *const dicNode) const {
        return false;
    }

    AK_FORCE_INLINE bool canDoLookAheadCorrection(
            const DicTraverseSession *const traverseSession, const DicNode *const dicNode) const {
        return false;
    }

    AK_FORCE_INLINE ProximityType getProximityType(
            const DicTraverseSession *const traverseSession, const DicNode *const dicNode,
            const DicNode *const childDicNode) const {
        const ProximityInfoState *const pInfoState = traverseSession->getProximityInfoState(0);
        if (!pInfoState->isUsed()) {
            return UNRELATED_CHAR;
        }
        const int inputSize = pInfoState->size();
        const int pointIndex = dicNode->getInputIndex(0);
        if (pointIndex < 0 || pointIndex >= inputSize) {
            return UNRELATED_CHAR;
        }
        const int lowerCodePoint = CharUtils::toBaseLowerCase(childDicNode->getNodeCodePoint());
        const int keyId = traverseSession->getProximityInfo()->getKeyIndexOf(lowerCodePoint);
        if (keyId == NOT_AN_INDEX) {
            return UNRELATED_CHAR;
        }

        // First letter of the word must be close to the start of the gesture path
        if (dicNode->isRoot()) {
            const float startDist = pInfoState->getPointToKeyByIdLength(0, keyId);
            const float secondDist = (inputSize > 1)
                    ? pInfoState->getPointToKeyByIdLength(1, keyId) : startDist;
            if (std::min(startDist, secondDist) <= 1.85f
                    || pInfoState->getProbability(0, keyId) < 5.5f) {
                return MATCH_CHAR;
            }
            return UNRELATED_CHAR;
        }

        // Subsequent letters must appear in the forward search keys from the current gesture point
        if (pInfoState->isKeyInSerchKeysAfterIndex(pointIndex, keyId)) {
            return MATCH_CHAR;
        }
        if (pointIndex > 0 && pInfoState->isKeyInSerchKeysAfterIndex(pointIndex - 1, keyId)) {
            return PROXIMITY_CHAR;
        }
        return UNRELATED_CHAR;
    }

    AK_FORCE_INLINE bool needsToTraverseAllUserInput() const {
        return true;
    }

    AK_FORCE_INLINE float getMaxSpatialDistance() const {
        return 4.0f;
    }

    AK_FORCE_INLINE int getDefaultExpandDicNodeSize() const {
        return DicNodeVector::DEFAULT_NODES_SIZE_FOR_OPTIMIZATION;
    }

    AK_FORCE_INLINE int getMaxCacheSize(const int inputSize, const float weightForLocale) const {
        return ScoringParams::MAX_CACHE_DIC_NODE_SIZE;
    }

    AK_FORCE_INLINE int getTerminalCacheSize() const {
        return MAX_RESULTS;
    }

    AK_FORCE_INLINE bool isPossibleOmissionChildNode(
            const DicTraverseSession *const traverseSession, const DicNode *const parentDicNode,
            const DicNode *const dicNode) const {
        const ProximityType proximityType =
                getProximityType(traverseSession, parentDicNode, dicNode);
        return ProximityInfoUtils::isMatchOrProximityChar(proximityType);
    }

    AK_FORCE_INLINE bool isGoodToTraverseNextWord(const DicNode *const dicNode,
            const int probability) const {
        return probability >= ScoringParams::THRESHOLD_NEXT_WORD_PROBABILITY;
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(GestureTraversal);
    static const GestureTraversal sInstance;

    GestureTraversal() {}
    ~GestureTraversal() {}
};

class GestureScoring : public Scoring {
 public:
    static const GestureScoring *getInstance() { return &sInstance; }

    AK_FORCE_INLINE void getMostProbableString(const DicTraverseSession *const traverseSession,
            const float weightOfLangModelVsSpatialModel,
            SuggestionResults *const outSuggestionResults) const {}

    AK_FORCE_INLINE float getAdjustedWeightOfLangModelVsSpatialModel(
            DicTraverseSession *const traverseSession, DicNode *const terminals,
            const int size) const {
        return 1.25f;
    }

    AK_FORCE_INLINE int calculateFinalScore(const float compoundDistance, const int inputSize,
            const ErrorTypeUtils::ErrorType containedErrorTypes, const bool forceCommit,
            const bool boostExactMatches, const bool hasProbabilityZero) const {
        const float normalizedScore = 2.0f - (compoundDistance / static_cast<float>(std::max(1, inputSize) + 3));
        float score = std::max(0.01f, normalizedScore);
        if (forceCommit) {
            score += ScoringParams::AUTOCORRECT_OUTPUT_THRESHOLD;
        }
        return static_cast<int>(score * SUGGEST_INTERFACE_OUTPUT_SCALE);
    }

    AK_FORCE_INLINE float getDoubleLetterDemotionDistanceCost(
            const DicNode *const terminalDicNode) const {
        switch (terminalDicNode->getDoubleLetterLevel()) {
            case NOT_A_DOUBLE_LETTER:
                return 0.35f;
            case A_DOUBLE_LETTER:
                return 0.10f;
            case A_STRONG_DOUBLE_LETTER:
                return 0.0f;
            default:
                return 0.0f;
        }
    }

    AK_FORCE_INLINE bool autoCorrectsToMultiWordSuggestionIfTop() const {
        return false;
    }

    AK_FORCE_INLINE bool sameAsTyped(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode) const {
        return false;
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(GestureScoring);
    static const GestureScoring sInstance;

    GestureScoring() {}
    ~GestureScoring() {}
};

class GestureWeighting : public Weighting {
 public:
    static const GestureWeighting *getInstance() { return &sInstance; }

 protected:
    float getTerminalSpatialCost(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode) const {
        const ProximityInfoState *const pInfoState = traverseSession->getProximityInfoState(0);
        if (!pInfoState->isUsed()) {
            return static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
        }
        const int inputSize = pInfoState->size();
        const int lastCharCode = CharUtils::toBaseLowerCase(dicNode->getNodeCodePoint());
        const int keyId = traverseSession->getProximityInfo()->getKeyIndexOf(lastCharCode);
        if (keyId == NOT_AN_INDEX) {
            return 2.5f;
        }
        // Strongly reward words whose final letter matches the end of the user's swipe trail
        const float endDist = pInfoState->getPointToKeyByIdLength(inputSize - 1, keyId);
        const float endProbCost = pInfoState->getProbability(inputSize - 1, keyId);
        const float tailCost = std::min(endDist * 0.85f, endProbCost * 0.35f);

        // Penalize unconsumed gesture trail points
        const int consumedIndex = dicNode->getInputIndex(0);
        const int remainingPoints = std::max(0, inputSize - consumedIndex);
        const float unconsumedPenalty = static_cast<float>(remainingPoints) * 0.22f;

        return tailCost + unconsumedPenalty;
    }

    float getOmissionCost(const DicNode *const parentDicNode, const DicNode *const dicNode) const {
        if (parentDicNode->isZeroCostOmission()) {
            return 0.0f;
        }
        if (parentDicNode->canBeIntentionalOmission()) {
            return ScoringParams::INTENTIONAL_OMISSION_COST;
        }
        return dicNode->isSameNodeCodePoint(parentDicNode) ? 0.18f : 0.65f;
    }

    float getMatchedCost(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode, DicNode_InputStateG *inputStateG) const {
        const ProximityInfoState *const pInfoState = traverseSession->getProximityInfoState(0);
        const int inputSize = pInfoState->size();
        const int startIndex = std::min(static_cast<int>(dicNode->getInputIndex(0)),
                std::max(0, inputSize - 1));
        const int codePoint = CharUtils::toBaseLowerCase(dicNode->getNodeCodePoint());
        const int keyId = traverseSession->getProximityInfo()->getKeyIndexOf(codePoint);
        if (keyId == NOT_AN_INDEX || inputSize <= 0) {
            return 3.0f;
        }

        const int prevCodePoint = dicNode->getPrevCodePointG(0);
        const bool isSameAsPrev = (prevCodePoint == codePoint);

        // If this is a double letter (e.g., "ll" in "hello"), don't advance the gesture point
        if (isSameAsPrev) {
            inputStateG->mNeedsToUpdateInputStateG = true;
            inputStateG->mPointerId = 0;
            inputStateG->mInputIndex = static_cast<int16_t>(startIndex);
            inputStateG->mPrevCodePoint = codePoint;
            inputStateG->mTerminalDiffCost = 0.0f;
            inputStateG->mRawLength = 0.0f;
            inputStateG->mDoubleLetterLevel = pInfoState->getDoubleLetterLevel(
                    std::max(0, startIndex - 1));
            const float curDist = pInfoState->getPointToKeyByIdLength(
                    std::max(0, startIndex - 1), keyId);
            return std::min(0.45f, curDist * 0.25f);
        }

        // First character of the gesture word: anchor near index 0
        if (dicNode->getNodeCodePointCount() <= 1) {
            const int bestIdx = (inputSize > 1
                    && pInfoState->getPointToKeyByIdLength(1, keyId) + 0.15f
                            < pInfoState->getPointToKeyByIdLength(0, keyId)) ? 1 : 0;
            const float dist = pInfoState->getPointToKeyByIdLength(bestIdx, keyId);
            const float probCost = pInfoState->getProbability(bestIdx, keyId);
            const float cost = std::min(dist * 0.9f, probCost * 0.30f);

            inputStateG->mNeedsToUpdateInputStateG = true;
            inputStateG->mPointerId = 0;
            inputStateG->mInputIndex = static_cast<int16_t>(std::min(inputSize, bestIdx + 1));
            inputStateG->mPrevCodePoint = codePoint;
            inputStateG->mTerminalDiffCost = cost;
            inputStateG->mRawLength = 0.0f;
            inputStateG->mDoubleLetterLevel = NOT_A_DOUBLE_LETTER;
            return cost;
        }

        // Search forward along the sampled gesture points for the best local alignment to keyId
        int bestIndex = startIndex;
        float bestCost = static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
        float accumulatedSkipPenalty = 0.0f;

        for (int idx = startIndex; idx < inputSize; ++idx) {
            const float sqDist = pInfoState->getPointToKeyByIdLength(idx, keyId);
            const float logProb = pInfoState->getProbability(idx, keyId);
            const float alignCost = std::min(sqDist * 0.70f, logProb * 0.28f);
            const float totalCost = alignCost + accumulatedSkipPenalty;

            if (totalCost < bestCost) {
                bestCost = totalCost;
                bestIndex = idx;
            }

            //Probability of skipping point `idx` along the path
            const float skipLogProb = pInfoState->getProbability(idx, NOT_AN_INDEX);
            accumulatedSkipPenalty += std::min(0.24f, std::max(0.02f, skipLogProb * 0.10f));

            // Stop looking further if skip penalty already exceeds best match found
            if (accumulatedSkipPenalty > bestCost + 0.5f) {
                break;
            }
        }

        const int nextIndex = std::min(inputSize, bestIndex + 1);
        const float rawLenDelta = (bestIndex > startIndex)
                ? static_cast<float>(pInfoState->getLengthCache(bestIndex)
                        - pInfoState->getLengthCache(startIndex))
                : 0.0f;

        inputStateG->mNeedsToUpdateInputStateG = true;
        inputStateG->mPointerId = 0;
        inputStateG->mInputIndex = static_cast<int16_t>(nextIndex);
        inputStateG->mPrevCodePoint = codePoint;
        inputStateG->mTerminalDiffCost = bestCost;
        inputStateG->mRawLength = rawLenDelta;
        inputStateG->mDoubleLetterLevel = pInfoState->getDoubleLetterLevel(bestIndex);

        return bestCost;
    }

    bool isProximityDicNode(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode) const {
        return false;
    }

    float getTranspositionCost(const DicTraverseSession *const traverseSession,
            const DicNode *const parentDicNode, const DicNode *const dicNode) const {
        return ScoringParams::TRANSPOSITION_COST;
    }

    float getInsertionCost(const DicTraverseSession *const traverseSession,
            const DicNode *const parentDicNode, const DicNode *const dicNode) const {
        return ScoringParams::INSERTION_COST;
    }

    float getSpaceOmissionCost(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode, DicNode_InputStateG *const inputStateG) const {
        return ScoringParams::SPACE_OMISSION_COST;
    }

    float getNewWordBigramLanguageCost(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode, MultiBigramMap *const multiBigramMap) const {
        return DicNodeUtils::getBigramNodeImprobability(
                traverseSession->getDictionaryStructurePolicy(),
                dicNode, multiBigramMap) * ScoringParams::DISTANCE_WEIGHT_LANGUAGE;
    }

    float getCompletionCost(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode) const {
        return 0.55f;
    }

    float getTerminalInsertionCost(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode) const {
        const int inputIndex = dicNode->getInputIndex(0);
        const int inputSize = traverseSession->getInputSize();
        if (inputIndex >= inputSize) {
            return 0.0f;
        }
        return 0.18f * static_cast<float>(inputSize - inputIndex);
    }

    float getTerminalLanguageCost(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode, const float dicNodeLanguageImprobability) const {
        return dicNodeLanguageImprobability * ScoringParams::DISTANCE_WEIGHT_LANGUAGE * 1.15f;
    }

    AK_FORCE_INLINE bool needsToNormalizeCompoundDistance() const {
        return false;
    }

    AK_FORCE_INLINE float getAdditionalProximityCost() const {
        return ScoringParams::ADDITIONAL_PROXIMITY_COST;
    }

    AK_FORCE_INLINE float getSubstitutionCost() const {
        return ScoringParams::SUBSTITUTION_COST;
    }

    AK_FORCE_INLINE float getSpaceSubstitutionCost(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode) const {
        return ScoringParams::SPACE_SUBSTITUTION_COST;
    }

    ErrorTypeUtils::ErrorType getErrorType(const CorrectionType correctionType,
            const DicTraverseSession *const traverseSession,
            const DicNode *const parentDicNode, const DicNode *const dicNode) const {
        switch (correctionType) {
            case CT_MATCH:
            case CT_TERMINAL:
                return ErrorTypeUtils::NOT_AN_ERROR;
            case CT_OMISSION:
                return parentDicNode->canBeIntentionalOmission()
                        ? ErrorTypeUtils::INTENTIONAL_OMISSION
                        : ErrorTypeUtils::EDIT_CORRECTION;
            case CT_COMPLETION:
                return ErrorTypeUtils::COMPLETION;
            default:
                return ErrorTypeUtils::PROXIMITY_CORRECTION;
        }
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(GestureWeighting);
    static const GestureWeighting sInstance;

    GestureWeighting() {}
    ~GestureWeighting() {}
};

class GestureSuggestPolicy : public SuggestPolicy {
 public:
    static const SuggestPolicy *getInstance() { return &sInstance; }

    GestureSuggestPolicy() {}
    virtual ~GestureSuggestPolicy() {}

    AK_FORCE_INLINE const Traversal *getTraversal() const {
        return GestureTraversal::getInstance();
    }

    AK_FORCE_INLINE const Scoring *getScoring() const {
        return GestureScoring::getInstance();
    }

    AK_FORCE_INLINE const Weighting *getWeighting() const {
        return GestureWeighting::getInstance();
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(GestureSuggestPolicy);
    static const GestureSuggestPolicy sInstance;
};

} // namespace latinime
#endif // LATINIME_GESTURE_SUGGEST_POLICY_H
