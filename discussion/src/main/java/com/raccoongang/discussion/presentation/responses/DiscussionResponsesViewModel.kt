package com.raccoongang.discussion.presentation.responses

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.raccoongang.core.BaseViewModel
import com.raccoongang.core.R
import com.raccoongang.core.SingleEventLiveData
import com.raccoongang.core.UIMessage
import com.raccoongang.core.data.storage.PreferencesManager
import com.raccoongang.core.extension.isInternetError
import com.raccoongang.core.system.ResourceManager
import com.raccoongang.discussion.domain.interactor.DiscussionInteractor
import com.raccoongang.discussion.domain.model.DiscussionComment
import com.raccoongang.discussion.domain.model.DiscussionProfile
import com.raccoongang.discussion.system.notifier.DiscussionCommentDataChanged
import com.raccoongang.discussion.system.notifier.DiscussionNotifier
import kotlinx.coroutines.launch

class DiscussionResponsesViewModel(
    private val interactor: DiscussionInteractor,
    private val resourceManager: ResourceManager,
    private val preferencesManager: PreferencesManager,
    private val notifier: DiscussionNotifier,
    private var comment: DiscussionComment,
) : BaseViewModel() {

    private val _uiState = MutableLiveData<DiscussionResponsesUIState>()
    val uiState: LiveData<DiscussionResponsesUIState>
        get() = _uiState

    private val _uiMessage = SingleEventLiveData<UIMessage>()
    val uiMessage: LiveData<UIMessage>
        get() = _uiMessage

    private val _canLoadMore = MutableLiveData<Boolean>()
    val canLoadMore: LiveData<Boolean>
        get() = _canLoadMore

    private val _isUpdating = MutableLiveData<Boolean>()
    val isUpdating: LiveData<Boolean>
        get() = _isUpdating

    var isThreadClosed: Boolean = false

    private val comments = mutableListOf<DiscussionComment>()
    private var page = 1
    private var isLoading = false

    private suspend fun sendUpdatedComment() {
        notifier.send(DiscussionCommentDataChanged(comment))
    }

    init {
        loadCommentResponses()
    }

    private fun loadCommentResponses() {
        _uiState.value = DiscussionResponsesUIState.Loading
        loadCommentsInternal()
    }

    fun updateCommentResponses() {
        _isUpdating.value = true
        page = 1
        comments.clear()
        loadCommentsInternal()
    }

    fun fetchMore() {
        if (!isLoading && page != -1) {
            loadCommentsInternal()
        }
    }

    private fun loadCommentsInternal() {
        viewModelScope.launch {
            try {
                isLoading = true
                val response = interactor.getCommentsResponses(comment.id, page)
                if (response.pagination.next.isNotEmpty()) {
                    _canLoadMore.value = true
                    page++
                } else {
                    _canLoadMore.value = false
                    page = -1
                }
                comments.addAll(response.results)
                _uiState.value = DiscussionResponsesUIState.Success(comment, comments.toList())
            } catch (e: Exception) {
                if (e.isInternetError()) {
                    _uiMessage.value =
                        UIMessage.SnackBarMessage(resourceManager.getString(R.string.core_error_no_connection))
                } else {
                    _uiMessage.value =
                        UIMessage.SnackBarMessage(resourceManager.getString(R.string.core_error_unknown_error))
                }
            } finally {
                isLoading = false
                _isUpdating.value = false
            }
        }
    }

    fun setCommentUpvoted(commentId: String, vote: Boolean) {
        viewModelScope.launch {
            try {
                val response = interactor.setCommentVoted(commentId, vote)
                val index = comments.indexOfFirst {
                    it.id == response.id
                }
                if (index != -1) {
                    comments[index] =
                        comments[index].copy(voted = response.voted, voteCount = response.voteCount)
                } else {
                    comment = comment.copy(voted = response.voted, voteCount = response.voteCount)
                    sendUpdatedComment()
                }
                _uiState.value = DiscussionResponsesUIState.Success(comment, comments.toList())
            } catch (e: Exception) {
                if (e.isInternetError()) {
                    _uiMessage.value =
                        UIMessage.SnackBarMessage(resourceManager.getString(R.string.core_error_no_connection))
                } else {
                    _uiMessage.value =
                        UIMessage.SnackBarMessage(resourceManager.getString(R.string.core_error_unknown_error))
                }
            }
        }
    }

    fun setCommentReported(commentId: String, vote: Boolean) {
        viewModelScope.launch {
            try {
                val response = interactor.setCommentFlagged(commentId, vote)
                val index = comments.indexOfFirst {
                    it.id == response.id
                }
                if (index != -1) {
                    comments[index] = comments[index].copy(abuseFlagged = response.abuseFlagged)
                } else {
                    comment = comment.copy(abuseFlagged = response.abuseFlagged)
                    sendUpdatedComment()
                }
                _uiState.value = DiscussionResponsesUIState.Success(comment, comments.toList())
            } catch (e: Exception) {
                if (e.isInternetError()) {
                    _uiMessage.value =
                        UIMessage.SnackBarMessage(resourceManager.getString(R.string.core_error_no_connection))
                } else {
                    _uiMessage.value =
                        UIMessage.SnackBarMessage(resourceManager.getString(R.string.core_error_unknown_error))
                }
            }
        }
    }

    fun createComment(rawBody: String) {
        viewModelScope.launch {
            try {
                var response = interactor.createComment(comment.threadId, rawBody, comment.id)
                response = response.copy(
                    users = mapOf(
                        Pair(
                            preferencesManager.profile?.username ?: "",
                            DiscussionProfile(preferencesManager.profile?.profileImage)
                        )
                    )
                )
                comment = comment.copy(childCount = comment.childCount + 1)
                sendUpdatedComment()
                if (page == -1) {
                    comments.add(response)
                } else {
                    _uiMessage.value =
                        UIMessage.ToastMessage(resourceManager.getString(com.raccoongang.discussion.R.string.discussion_comment_added))
                }
                _uiState.value =
                    DiscussionResponsesUIState.Success(comment, comments.toList())
            } catch (e: Exception) {
                if (e.isInternetError()) {
                    _uiMessage.value =
                        UIMessage.SnackBarMessage(resourceManager.getString(R.string.core_error_no_connection))
                } else {
                    _uiMessage.value =
                        UIMessage.SnackBarMessage(resourceManager.getString(R.string.core_error_unknown_error))
                }
            }
        }
    }

}